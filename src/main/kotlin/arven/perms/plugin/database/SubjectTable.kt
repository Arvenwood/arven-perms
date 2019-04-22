package arven.perms.plugin.database

import arven.perms.plugin.util.collectAll
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select

object SubjectTable : LongIdTable("subjects") {
    val identifier = varchar("identifier", 128).index()
    val collection = reference("collection", SubjectCollectionTable, onDelete = ReferenceOption.CASCADE).index()
    val displayName = varchar("display_name", 64).nullable().index()
    val weight = integer("weight").default(0).index()

    init {
        uniqueIndex(identifier, collection)
    }
}

class SubjectEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SubjectEntity>(SubjectTable) {
        fun find(collectionId: EntityID<Int>, identifier: String): SubjectEntity? = find {
            (SubjectTable.collection eq collectionId) and (SubjectTable.identifier eq identifier)
        }.singleOrNull()

        fun find(collectionIdentifier: String, subjectIdentifier: String): SubjectEntity? {
            val row = SubjectTable.innerJoin(SubjectCollectionTable)
                .slice(SubjectTable.columns)
                .select {
                    (SubjectTable.collection eq SubjectCollectionTable.id) and
                            (SubjectTable.identifier eq subjectIdentifier) and
                            (SubjectCollectionTable.identifier eq collectionIdentifier)
                }.singleOrNull()

            return row?.let { SubjectEntity.wrapRow(it) }
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

    val inheritanceList: ArrayList<SubjectEntity>
        get() {
            val list = this.collectAll { it.parents }
            list.sortByDescending { it.weight }
            return list
        }
}

val SubjectEntity.effectiveName: String get() = this.displayName ?: this.identifier