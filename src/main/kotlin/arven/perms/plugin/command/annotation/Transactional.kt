package arven.perms.plugin.command.annotation

import arven.perms.plugin.ArvenPerms.Companion.DB
import frontier.skc.annotation.ExecutionTransformingAnnotation
import frontier.skc.transform.ExecutionContext
import frontier.skc.transform.ExecutionTransformer
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.command.CommandSource

@ExecutionTransformingAnnotation
annotation class Transactional {
    companion object : ExecutionTransformer<Transactional> {
        override fun transformExecution(src: CommandSource, context: ExecutionContext, annotation: Transactional,
                                        next: () -> CommandResult): CommandResult {
            return transaction(DB) {
                next()
            }
        }
    }
}