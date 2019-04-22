package arven.perms.plugin.database

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

object SubjectParentTable : Table("subject_parents") {
    val child = reference("child", SubjectTable, onDelete = ReferenceOption.CASCADE).index()
    val parent = reference("parent", SubjectTable, onDelete = ReferenceOption.CASCADE).index()

    init {
        uniqueIndex(child, parent)
    }
}