package arven.perms.plugin.command.value

import arven.perms.plugin.ArvenPerms.Companion.DB
import arven.perms.plugin.database.SubjectCollectionEntity
import arven.perms.plugin.database.SubjectCollectionTable
import arven.perms.plugin.database.SubjectTable
import frontier.skpc.util.RTuple
import frontier.skpc.value.ValueCompleter
import frontier.skpc.value.completer
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction

object ArvenValueCompleters {

    val subjectCollectionEntity: ValueCompleter<Any?> =
        { _, _, _ ->
            transaction(DB) {
                SubjectCollectionEntity.all()
                    .orderBy(SubjectCollectionTable.identifier to SortOrder.ASC)
                    .map { it.identifier }
            }
        }

    val subjectEntity: ValueCompleter<RTuple<SubjectCollectionEntity, *>> =
        completer { _, _, collection ->
            transaction(DB) {
                collection.subjects
                    .orderBy(SubjectTable.identifier to SortOrder.ASC)
                    .map { it.identifier }
            }
        }

    fun subjectEntityOf(collectionIdentifier: String): ValueCompleter<Any?> =
        { _, _, _ ->
            transaction(DB) {
                SubjectCollectionEntity.find(collectionIdentifier)?.subjects
                    ?.orderBy(SubjectTable.identifier to SortOrder.ASC)
                    ?.map { it.identifier }
                    .orEmpty()
            }
        }

    fun subjectEntityByDisplayNameOf(collectionIdentifier: String): ValueCompleter<Any?> =
        { _, _, _ ->
            transaction(DB) {
                SubjectCollectionEntity.find(collectionIdentifier)?.subjects
                    ?.orderBy(SubjectTable.displayName to SortOrder.ASC)
                    ?.mapNotNull { it.displayName }
                    .orEmpty()
            }
        }
}