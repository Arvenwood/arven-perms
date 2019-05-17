package arven.perms.plugin.command

import arven.perms.plugin.ArvenPerms.Companion.DB
import frontier.skpc.CommandValueExecutor
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.command.CommandResult

inline fun <Source, Value> transactional(
    database: Database = DB,
    crossinline block: CommandValueExecutor<Source, Value>
): CommandValueExecutor<Source, Value> = { src, value ->
    transaction(database) {
        block(src, value)
        CommandResult.success()
    }
}