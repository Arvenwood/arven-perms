package arven.perms.plugin.command.annotation

@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class OnlyCollection(val collection: String)