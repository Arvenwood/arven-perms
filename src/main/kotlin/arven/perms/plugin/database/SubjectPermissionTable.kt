package arven.perms.plugin.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object SubjectPermissionTable : LongIdTable("subject_permissions") {
    val subject = reference("subject", SubjectTable, onDelete = ReferenceOption.CASCADE).index()
    val permission = varchar("permission", 255).index()
    val value = bool("value")

    init {
        uniqueIndex(subject, permission)
    }
}

class SubjectPermissionEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SubjectPermissionEntity>(SubjectPermissionTable)

    var subject by SubjectEntity referencedOn SubjectPermissionTable.subject

    var permission by SubjectPermissionTable.permission

    var value by SubjectPermissionTable.value
}