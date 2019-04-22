package arven.perms.plugin.database

import arven.perms.plugin.util.toTreeList
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or

object SubjectPermissionTable : LongIdTable("subject_permissions") {
    val subject = reference("subject", SubjectTable, onDelete = ReferenceOption.CASCADE).index()
    val permission = varchar("permission", 255).index()
    val value = bool("value")

    init {
        uniqueIndex(subject, permission)
    }
}

class SubjectPermissionEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SubjectPermissionEntity>(SubjectPermissionTable) {

        fun getValue(subject: SubjectEntity, permission: String): Boolean? =
            SubjectPermissionEntity
                .find {
                    SubjectPermissionTable.subject eq subject.id and permission.toTreeList()
                        .map { (SubjectPermissionTable.permission eq it) }
                        .reduce { first, second ->
                            first or second
                        }
                }
                .singleOrNull()
                ?.value

        fun getValueDeep(subject: SubjectEntity, permission: String): Triple<SubjectEntity, String, Boolean>? {
            for (branch in subject.inheritanceList) {
                val entity = SubjectPermissionEntity
                    .find {
                        (SubjectPermissionTable.subject eq branch.id) and permission.toTreeList()
                            .map { (SubjectPermissionTable.permission eq it) }
                            .reduce { first, second ->
                                first or second
                            }
                    }
                    .singleOrNull()

                if (entity != null) {
                    return Triple(branch, entity.permission, entity.value)
                }
            }

            return null
        }

        fun getPermission(subject: SubjectEntity, permission: String): SubjectPermissionEntity? =
            SubjectPermissionEntity.find {
                (SubjectPermissionTable.subject eq subject.id) and (SubjectPermissionTable.permission eq permission)
            }.singleOrNull()

        fun setPermission(subject: SubjectEntity, permission: String, value: Boolean?): Boolean {
            val entity = this.getPermission(subject, permission)

            return when {
                entity == null && value != null -> {
                    this.new {
                        this.subject = subject
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
    }

    var subject by SubjectEntity referencedOn SubjectPermissionTable.subject

    var permission by SubjectPermissionTable.permission

    var value by SubjectPermissionTable.value
}