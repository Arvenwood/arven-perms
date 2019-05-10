package arven.perms.plugin.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object SubjectOptionTable : LongIdTable("subject_options") {
    val subject = reference("subject", SubjectTable, onDelete = ReferenceOption.CASCADE).index()
    val key = varchar("key", 255).index()
    val value = text("value")

    init {
        uniqueIndex(subject, key)
    }
}

class SubjectOptionEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<SubjectOptionEntity>(SubjectOptionTable)

    var subject by SubjectEntity referencedOn SubjectOptionTable.subject

    var key by SubjectOptionTable.key

    var value by SubjectOptionTable.value
}