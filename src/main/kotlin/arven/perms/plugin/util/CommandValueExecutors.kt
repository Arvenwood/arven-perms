package arven.perms.plugin.util

import arven.perms.plugin.ArvenPerms.Companion.DB
import frontier.skpc.CommandValueExecutor
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.command.CommandResult

inline fun <T> transactional(
    database: Database = DB,
    crossinline block: (T) -> Unit
): CommandValueExecutor<in T> = {
    transaction(database) {
        block(it)
        CommandResult.success()
    }
}

inline fun <T> noReturn(crossinline block: (T) -> Unit): CommandValueExecutor<in T> = {
    block(it)
    CommandResult.success()
}