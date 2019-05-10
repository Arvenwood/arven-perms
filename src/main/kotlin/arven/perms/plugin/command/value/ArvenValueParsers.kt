package arven.perms.plugin.command.value

import arven.perms.plugin.ArvenPerms.Companion.DB
import arven.perms.plugin.database.SubjectCollectionEntity
import arven.perms.plugin.database.SubjectEntity
import frontier.ske.text.not
import frontier.skpc.util.RTuple
import frontier.skpc.value.ValueParser
import frontier.skpc.value.parser
import org.jetbrains.exposed.sql.transactions.transaction

object ArvenValueParsers {

    val subjectCollectionEntity: ValueParser<Any?, SubjectCollectionEntity> =
        { _, args, _ ->
            transaction(DB) {
                val identifier = args.next()
                SubjectCollectionEntity.find(identifier)
                    ?: throw args.createError(!"Could not find any subject collection named '$identifier'")
            }
        }

    val subjectEntity: ValueParser<RTuple<SubjectCollectionEntity, *>, SubjectEntity> =
        parser { _, args, collection ->
            transaction(DB) {
                val identifier = args.next()
                SubjectEntity.find(collection.id, identifier)
                    ?: throw args.createError(!"Could not find any ${collection.identifier} named '$identifier'")
            }
        }

    fun subjectEntityOf(collectionIdentifier: String): ValueParser<Any?, SubjectEntity> =
        { _, args, _ ->
            transaction(DB) {
                val identifier = args.next()
                SubjectEntity.find(collectionIdentifier, identifier)
                    ?: throw args.createError(!"Could not find any $collectionIdentifier named '$identifier'")
            }
        }

    fun subjectEntityByDisplayNameOf(collectionIdentifier: String): ValueParser<Any?, SubjectEntity> =
        { _, args, _ ->
            transaction(DB) {
                val identifier = args.next()
                SubjectEntity.findByDisplayName(collectionIdentifier, identifier)
                    ?: throw args.createError(!"Could not find any $collectionIdentifier named '$identifier'")
            }
        }
}