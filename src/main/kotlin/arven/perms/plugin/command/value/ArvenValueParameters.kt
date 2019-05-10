package arven.perms.plugin.command.value

import frontier.skpc.value.ValueParameter
import frontier.skpc.value.standard.ValueUsages

object ArvenValueParameters {

    val subjectCollectionEntity = ValueParameter(
        ArvenValueParsers.subjectCollectionEntity,
        ArvenValueCompleters.subjectCollectionEntity,
        ValueUsages.single
    )

    val subjectEntity = ValueParameter(
        ArvenValueParsers.subjectEntity,
        ArvenValueCompleters.subjectEntity,
        ValueUsages.single
    )

    fun subjectEntityOf(collectionIdentifier: String) = ValueParameter(
        ArvenValueParsers.subjectEntityOf(collectionIdentifier),
        ArvenValueCompleters.subjectEntityOf(collectionIdentifier),
        ValueUsages.single
    )

    fun subjectEntityByDisplayNameOf(collectionIdentifier: String) = ValueParameter(
        ArvenValueParsers.subjectEntityByDisplayNameOf(collectionIdentifier),
        ArvenValueCompleters.subjectEntityByDisplayNameOf(collectionIdentifier),
        ValueUsages.single
    )
}