package arven.perms.plugin.command.annotation

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class CollectionKey(val name: String = "collection")