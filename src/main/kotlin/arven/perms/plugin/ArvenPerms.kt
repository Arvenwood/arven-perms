package arven.perms.plugin

import arven.perms.plugin.command.CommandAP
import arven.perms.plugin.command.match.databaseEntities
import arven.perms.plugin.database.*
import arven.perms.plugin.listener.ConnectionListener
import arven.perms.plugin.service.ArvenPermissionService
import com.google.inject.Inject
import frontier.skc.KClassCallable
import frontier.skc.match.SKCMatcher
import frontier.skc.value.*
import frontier.ske.eventManager
import frontier.ske.scheduler
import frontier.ske.service.require
import frontier.ske.service.setProvider
import frontier.ske.serviceManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.Logger
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.game.state.GameInitializationEvent
import org.spongepowered.api.plugin.Plugin
import org.spongepowered.api.service.permission.PermissionService
import org.spongepowered.api.service.sql.SqlService

@Plugin(
    id = "arven-perms", name = "Arven Perms", version = "0.0.0",
    authors = ["doot"], url = "https://github.com/Arvenwood/arven-perms",
    description = "A Permissions plugin."
)
class ArvenPerms @Inject constructor(logger: Logger) {

    init {
        LOGGER = logger
    }

    companion object {
        lateinit var SYNC: CoroutineDispatcher
        lateinit var ASYNC: CoroutineDispatcher

        lateinit var DB: Database

        lateinit var LOGGER: Logger
    }

    @Listener
    fun onInit(event: GameInitializationEvent) {
        SYNC = scheduler.createSyncExecutor(this).asCoroutineDispatcher()
        ASYNC = scheduler.createAsyncExecutor(this).asCoroutineDispatcher()

        setupDatabase()
        registerServices()
        registerListeners()
        registerCommands()
    }

    private fun setupDatabase() {
        LOGGER.info("Setting up the database...")

        val ds = serviceManager.require<SqlService>().getDataSource("jdbc:h2:./data/arven-perms/perms")
        DB = Database.connect(ds)

        transaction(DB) {
            SchemaUtils.create(SubjectCollectionTable, SubjectOptionTable, SubjectParentTable, SubjectPermissionTable, SubjectTable)
        }
    }

    private fun registerServices() {
        LOGGER.info("Registering services...")

        val service = ArvenPermissionService()
        serviceManager.setProvider<PermissionService>(this, service)

        LOGGER.info("Loading main subject collections...")
        service.loadCollection(PermissionService.SUBJECTS_COMMAND_BLOCK)
        service.loadCollection(PermissionService.SUBJECTS_DEFAULT)
        service.loadCollection(PermissionService.SUBJECTS_GROUP)
        service.loadCollection(PermissionService.SUBJECTS_ROLE_TEMPLATE)
        service.loadCollection(PermissionService.SUBJECTS_SYSTEM)
        service.loadCollection(PermissionService.SUBJECTS_USER)
    }

    private fun registerListeners() {
        LOGGER.info("Registering listeners...")

        eventManager.registerListeners(this, ConnectionListener())
    }

    private fun registerCommands() {
        LOGGER.info("Registering commands...")

        val matcher = SKCMatcher().apply {
            this.sources()
            this.player()
            this.string()
            this.int()
            this.bool()
            this.databaseEntities()
        }

        KClassCallable<CommandAP>(matcher).register(this)
    }
}