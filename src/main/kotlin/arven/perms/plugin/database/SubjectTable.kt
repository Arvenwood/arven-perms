package arven.perms.plugin.database

import arven.perms.plugin.service.ArvenSubject
import arven.perms.plugin.util.collectAllGuarded
import arven.perms.plugin.util.isNotEmpty
import arven.perms.plugin.util.toTreeList
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.*
import org.spongepowered.api.service.permission.Subject
import org.spongepowered.api.service.permission.SubjectCollection

object SubjectTable : LongIdTable("subjects") {
    val identifier = varchar("identifier", 128).index()
    val collection = reference("collection", SubjectCollectionTable, onDelete = ReferenceOption.CASCADE).index()
    val displayName = varchar("display_name", 64).nullable().index()
    val weight = integer("weight").default(0).index()

    init {
        uniqueIndex(identifier, collection)
    }

    fun has(collectionId: EntityID<Int>, identifier: String): Boolean =
        SubjectTable.select {
            (SubjectTable.collection eq collectionId) and
                    (SubjectTable.identifier eq identifier)
        }.isNotEmpty

    fun has(collectionIdentifier: String, identifier: String): Boolean =
        SubjectTable.innerJoin(SubjectCollectionTable).select {
            (SubjectTable.collection eq SubjectCollectionTable.id) and
                    (SubjectCollectionTable.identifier eq collectionIdentifier) and
                    (SubjectTable.identifier eq identifier)
        }.isNotEmpty

    fun <C : MutableCollection<in String>> allIdentifiers(collectionId: EntityID<Int>, destination: C): C =
        SubjectTable.slice(SubjectTable.identifier)
            .select { SubjectTable.collection eq collectionId }
            .mapTo(destination) { it[SubjectTable.identifier] }
}

class SubjectEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SubjectEntity>(SubjectTable) {

        fun all(collectionId: EntityID<Int>): SizedIterable<SubjectEntity> =
            find { (SubjectTable.collection eq collectionId) }

        fun find(collectionId: EntityID<Int>, identifier: String): SubjectEntity? =
            find { (SubjectTable.collection eq collectionId) and (SubjectTable.identifier eq identifier) }
                .singleOrNull()

        fun find(collectionIdentifier: String, subjectIdentifier: String): SubjectEntity? {
            val row = SubjectTable.innerJoin(SubjectCollectionTable)
                .slice(SubjectTable.columns)
                .select {
                    (SubjectTable.collection eq SubjectCollectionTable.id) and
                            (SubjectTable.identifier eq subjectIdentifier) and
                            (SubjectCollectionTable.identifier eq collectionIdentifier)
                }.singleOrNull()

            return row?.let { wrapRow(it) }
        }

        fun findByDisplayName(collectionId: EntityID<Int>, displayName: String): SubjectEntity? = find {
            (SubjectTable.collection eq collectionId) and (SubjectTable.displayName eq displayName)
        }.singleOrNull()

        fun findByDisplayName(collectionIdentifier: String, displayName: String): SubjectEntity? {
            val row = SubjectTable.innerJoin(SubjectCollectionTable)
                .slice(SubjectTable.columns)
                .select {
                    (SubjectTable.collection eq SubjectCollectionTable.id) and
                            (SubjectTable.displayName eq displayName) and
                            (SubjectCollectionTable.identifier eq collectionIdentifier)
                }.singleOrNull()

            return row?.let { wrapRow(it) }
        }

        fun getOrCreate(collection: SubjectCollectionEntity, identifier: String): SubjectEntity =
            find(collection.id, identifier)
                ?: new {
                    this.identifier = identifier
                    this.collection = collection
                    this.displayName = null
                    this.weight = collection.defaultWeight
                }
    }

    var identifier by SubjectTable.identifier

    var collection by SubjectCollectionEntity referencedOn SubjectTable.collection

    var displayName by SubjectTable.displayName

    var weight by SubjectTable.weight

    var parents by SubjectEntity.via(SubjectParentTable.child, SubjectParentTable.parent)

    var children by SubjectEntity.via(SubjectParentTable.parent, SubjectParentTable.child)

    val permissions by SubjectPermissionEntity referrersOn SubjectPermissionTable.subject

    val options by SubjectOptionEntity referrersOn SubjectOptionTable.subject

    val inheritanceList: MutableList<SubjectEntity>
        get() {
            val list = this.collectAllGuarded { it.parents }
            list.sortByDescending { it.weight }
            return list
        }

    fun clearParents() {
        parents = SizedCollection()
    }

    fun clearPermissions() {
        SubjectPermissionTable
            .deleteWhere {
                exists(SubjectTable.select {
                    SubjectPermissionTable.subject eq SubjectTable.id
                })
            }
    }

    fun getPermissionValueShallow(permission: String): Boolean? =
        SubjectPermissionEntity.find {
            (SubjectPermissionTable.subject eq this@SubjectEntity.id) and
                    permission.toTreeList()
                        .map { (SubjectPermissionTable.permission eq it) }
                        .reduce { first, second -> first or second }
        }.singleOrNull()?.value

    fun getPermission(permission: String): Triple<SubjectEntity, String, Boolean>? =
        getPermission(permission, this)

    private fun getPermission(permission: String,
                              subject: SubjectEntity): Triple<SubjectEntity, String, Boolean>? {
        for (branch in subject.inheritanceList) {
            val entity = SubjectPermissionEntity.find {
                (SubjectPermissionTable.subject eq branch.id) and permission.toTreeList()
                    .map { (SubjectPermissionTable.permission eq it) }
                    .reduce { first, second ->
                        first or second
                    }
            }.singleOrNull()

            if (entity != null) {
                return Triple(branch, entity.permission, entity.value)
            }
        }

        return null
    }

    fun setPermissionValue(permission: String, value: Boolean?): Boolean {
        val entity = SubjectPermissionEntity.find {
            (SubjectPermissionTable.subject eq this@SubjectEntity.id) and (SubjectPermissionTable.permission eq permission)
        }.singleOrNull()

        return when {
            entity == null && value != null -> {
                SubjectPermissionEntity.new {
                    this.subject = this@SubjectEntity
                    this.permission = permission
                    this.value = value
                }
                true
            }
            entity != null && value != null -> {
                entity.value = value
                true
            }
            entity != null && value == null -> {
                entity.delete()
                true
            }
            else                            -> false
        }
    }

    fun clearOptions() {
        SubjectOptionTable
            .deleteWhere {
                exists(SubjectTable.select {
                    SubjectOptionTable.subject eq SubjectTable.id
                })
            }
    }

    fun getOptionValueShallow(key: String): String? =
        SubjectOptionEntity.find {
            (SubjectOptionTable.subject eq this@SubjectEntity.id) and (SubjectOptionTable.key eq key)
        }.singleOrNull()?.value

    fun getOption(key: String): Pair<SubjectEntity, String>? =
        getOption(key, this)

    private fun getOption(key: String, subject: SubjectEntity): Pair<SubjectEntity, String>? {
        for (branch in subject.inheritanceList) {
            val entity = SubjectOptionEntity.find {
                (SubjectOptionTable.subject eq branch.id) and (SubjectOptionTable.key eq key)
            }.singleOrNull()

            if (entity != null) {
                return branch to entity.value
            }
        }

        return null
    }

    fun setOptionValue(key: String, value: String?): Boolean {
        val entity = SubjectOptionEntity.find {
            (SubjectOptionTable.subject eq this@SubjectEntity.id) and (SubjectOptionTable.key eq key)
        }.singleOrNull()

        return when {
            entity == null && value != null -> {
                SubjectOptionEntity.new {
                    this.subject = this@SubjectEntity
                    this.key = key
                    this.value = value
                }
                true
            }
            entity != null && value != null -> {
                entity.value = value
                true
            }
            entity != null && value == null -> {
                entity.delete()
                true
            }
            else                            -> false
        }
    }

    val display: String get() = this.displayName ?: this.identifier

    fun toSponge(): Subject = ArvenSubject(this.collection.toSponge(), this.id, this.identifier)

    fun toSponge(collection: SubjectCollection): ArvenSubject = ArvenSubject(collection, this.id, this.identifier)
}