package arven.perms.plugin.command.match

import arven.perms.plugin.ArvenPerms.Companion.DB
import arven.perms.plugin.command.annotation.CollectionKey
import arven.perms.plugin.command.annotation.OnlyCollection
import arven.perms.plugin.database.SubjectCollectionEntity
import arven.perms.plugin.database.SubjectEntity
import frontier.skc.match.AnnotationMatch.onAnnotation
import frontier.skc.match.AnnotationMatch.onEmpty
import frontier.skc.match.SKCMatcher
import frontier.skc.match.TypeMatch.onType
import frontier.skc.match.and
import frontier.skc.match.or
import frontier.skc.util.findAnnotation
import frontier.skc.value.ValueUsages
import frontier.ske.text.not
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.text.Text

fun SKCMatcher.databaseEntities() {
    // SubjectEntity
    parseTyped(onEmpty() or onAnnotation<CollectionKey>()) { _, args, _ ->
        transaction(DB) {
            val collectionId = args.next()
            val subjectId = args.next()
            SubjectEntity.find(collectionId, subjectId)
                ?: throw args.createError(!"Could not find any $collectionId named '$subjectId'")
        }
    }
    complete(onType<SubjectEntity>() and (onEmpty() or onAnnotation<CollectionKey>())) { _, args, _ ->
        transaction(DB) {
            val next = args.nextIfPresent()
            if (next.isPresent) {
                SubjectCollectionEntity.find(next.get())?.subjects?.map { it.identifier }.orEmpty()
            } else {
                SubjectCollectionEntity.all().map { it.identifier }
            }
        }
    }
    usageComplex(onType<SubjectEntity>() and (onEmpty() or onAnnotation<CollectionKey>())) { _, annotations ->
        val collectionKey = annotations.findAnnotation<CollectionKey>()?.name

        if (collectionKey != null) {
            { _, key ->
                Text.of("<", collectionKey, "> <", key, ">")
            }
        } else {
            { _, key ->
                Text.of("<", key, "Collection> <", key, ">")
            }
        }
    }

    // SubjectEntity + OnlyCollection
    parseTyped(onAnnotation<OnlyCollection>()) { _, args, modifiers ->
        transaction(DB) {
            val collectionId = modifiers.findAnnotation<OnlyCollection>()!!.collection
            val subjectId = args.next()
            SubjectEntity.find(collectionId, subjectId)
                ?: throw args.createError(!"Could not find any $collectionId named '$subjectId'")
        }
    }
    complete(onType<SubjectEntity>() and onAnnotation<OnlyCollection>()) { src, args, modifiers ->
        transaction(DB) {
            val collectionId = modifiers.findAnnotation<OnlyCollection>()!!.collection
            SubjectCollectionEntity.find(collectionId)?.subjects?.map { it.identifier }.orEmpty()
        }
    }
    usage(onType<SubjectEntity>() and onAnnotation<OnlyCollection>(), ValueUsages.SINGLE)

    // SubjectCollectionEntity
    parseTyped(onEmpty()) { _, args, _ ->
        transaction(DB) {
            val identifier = args.next()
            SubjectCollectionEntity.find(identifier)
                ?: throw args.createError(!"Could not find any subject collection named '$identifier'")
        }
    }
    complete(onType<SubjectCollectionEntity>() and onEmpty()) { _, _, _ ->
        transaction(DB) {
            SubjectCollectionEntity.all().map { it.identifier }
        }
    }
    usage(onType<SubjectCollectionEntity>() and onEmpty(), ValueUsages.SINGLE)
}