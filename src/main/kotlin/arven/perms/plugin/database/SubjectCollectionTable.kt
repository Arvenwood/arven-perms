package arven.perms.plugin.database

import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable

object SubjectCollectionTable : IntIdTable("subject_collections") {
    val identifier = varchar("identifier", 128).uniqueIndex()
}

class SubjectCollectionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SubjectCollectionEntity>(SubjectCollectionTable) {
        fun find(identifier: String): SubjectCollectionEntity? =
            find { (SubjectCollectionTable.identifier eq identifier) }.singleOrNull()

        fun getOrCreate(identifier: String): SubjectCollectionEntity =
            find(identifier) ?: new { this.identifier = identifier }
    }

    var identifier by SubjectCollectionTable.identifier

    val subjects by SubjectEntity referrersOn SubjectTable.collection
}