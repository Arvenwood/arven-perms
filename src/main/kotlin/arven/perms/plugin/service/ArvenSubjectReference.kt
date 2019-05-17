package arven.perms.plugin.service

import arven.perms.plugin.ArvenPerms.Companion.DB
import arven.perms.plugin.database.SubjectCollectionEntity
import arven.perms.plugin.database.SubjectEntity
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.service.permission.Subject
import org.spongepowered.api.service.permission.SubjectReference
import java.util.concurrent.CompletableFuture

class ArvenSubjectReference(private val collectionIdentifier: String,
                            private val subjectIdentifier: String) : SubjectReference {

    override fun getCollectionIdentifier(): String = collectionIdentifier

    override fun getSubjectIdentifier(): String = subjectIdentifier

    override fun resolve(): CompletableFuture<Subject> =
        GlobalScope.future {
            transaction(DB) {
                SubjectEntity.getOrCreate(SubjectCollectionEntity.getOrCreate(collectionIdentifier), subjectIdentifier)
                    .toSponge()
            }
        }
}

fun SubjectReference.toEntity(): SubjectEntity? =
    SubjectEntity.find(this.collectionIdentifier, this.subjectIdentifier)