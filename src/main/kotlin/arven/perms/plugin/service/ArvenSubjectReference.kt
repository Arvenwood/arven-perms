package arven.perms.plugin.service

import arven.perms.plugin.ArvenPerms.Companion.DB
import arven.perms.plugin.database.SubjectCollectionTable
import arven.perms.plugin.database.SubjectEntity
import arven.perms.plugin.database.SubjectTable
import arven.perms.plugin.util.future
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.service.permission.Subject
import org.spongepowered.api.service.permission.SubjectReference
import java.util.concurrent.CompletableFuture

class ArvenSubjectReference(private val collectionIdentifier: String,
                            private val subjectIdentifier: String) : SubjectReference {

    override fun getCollectionIdentifier(): String = collectionIdentifier

    override fun getSubjectIdentifier(): String = subjectIdentifier

    override fun resolve(): CompletableFuture<Subject?> =
        transaction(DB) {
            val row = SubjectTable.innerJoin(SubjectCollectionTable)
                .slice(SubjectTable.id, SubjectTable.identifier, SubjectCollectionTable.id, SubjectCollectionTable.identifier)
                .select {
                    (SubjectTable.collection eq SubjectCollectionTable.id) and
                            (SubjectTable.identifier eq subjectIdentifier) and
                            (SubjectCollectionTable.identifier eq collectionIdentifier)
                }.singleOrNull() ?: return@transaction null.future

            ArvenSubject(
                collection = ArvenSubjectCollection(id = row[SubjectCollectionTable.id], identifier = row[SubjectCollectionTable.identifier]),
                id = row[SubjectTable.id],
                identifier = row[SubjectTable.identifier]
            ).future
        }
}

fun SubjectReference.toEntity(): SubjectEntity? =
    SubjectEntity.find(this.collectionIdentifier, this.subjectIdentifier)