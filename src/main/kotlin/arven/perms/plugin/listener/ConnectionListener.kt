package arven.perms.plugin.listener

import arven.perms.plugin.ArvenPerms.Companion.DB
import arven.perms.plugin.ArvenPerms.Companion.LOGGER
import arven.perms.plugin.database.SubjectCollectionEntity
import arven.perms.plugin.database.SubjectEntity
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.entity.living.player.Player
import org.spongepowered.api.event.Listener
import org.spongepowered.api.event.Order
import org.spongepowered.api.event.filter.Getter
import org.spongepowered.api.event.network.ClientConnectionEvent
import org.spongepowered.api.service.permission.PermissionService

class ConnectionListener {

    @Listener(order = Order.PRE)
    fun onJoin(event: ClientConnectionEvent.Join, @Getter("getTargetEntity") player: Player) {
        transaction(DB) {
            val collectionEntity = SubjectCollectionEntity.getOrCreate(PermissionService.SUBJECTS_USER)
            val entity = SubjectEntity.find(collectionEntity.id, player.uniqueId.toString())

            if (entity == null) {
                LOGGER.info("Indexing ${player.name} (${player.uniqueId}) into the database...")

                SubjectEntity.new {
                    this.identifier = player.uniqueId.toString()
                    this.collection = collectionEntity
                    this.displayName = player.name
                }
            }
        }
    }
}