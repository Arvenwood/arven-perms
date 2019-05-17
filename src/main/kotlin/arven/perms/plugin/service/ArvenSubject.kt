package arven.perms.plugin.service

import arven.perms.plugin.ArvenPerms.Companion.DB
import arven.perms.plugin.database.*
import arven.perms.plugin.util.toBoolean
import arven.perms.plugin.util.toTristate
import frontier.ske.server
import frontier.ske.util.toUUID
import frontier.ske.util.unwrap
import frontier.ske.util.wrap
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.service.context.Context
import org.spongepowered.api.service.permission.Subject
import org.spongepowered.api.service.permission.SubjectCollection
import org.spongepowered.api.service.permission.SubjectData
import org.spongepowered.api.service.permission.SubjectReference
import org.spongepowered.api.util.Tristate
import java.util.*
import java.util.concurrent.CompletableFuture

class ArvenSubject(
    private val collection: SubjectCollection,
    private val id: EntityID<Long>,
    private val identifier: String
) : Subject, SubjectData {

    companion object {
        fun fetch(collection: ArvenSubjectCollection, identifier: String): ArvenSubject =
            transaction(DB) {
                SubjectEntity.getOrCreate(collection.entity, identifier).toSponge(collection)
            }
    }

    private val subjectEntity: SubjectEntity
        get() = SubjectEntity[id]

    override fun getIdentifier(): String =
        identifier

    override fun getFriendlyIdentifier(): Optional<String> =
        Optional.ofNullable(transaction(DB) { subjectEntity.identifier })

    override fun getContainingCollection(): SubjectCollection =
        collection

    override fun asSubjectReference(): SubjectReference =
        ArvenSubjectReference(collection.identifier, identifier)

    override fun getCommandSource(): Optional<CommandSource> =
        try {
            server.getPlayer(identifier.toUUID()).map { it as? CommandSource }.unwrap().wrap()
        } catch (ignored: Exception) {
            Optional.empty()
        }

    //
    // Permissions
    //

    override fun getPermissionValue(ctx: Set<Context>, permission: String): Tristate =
        transaction(DB) {
            subjectEntity.getPermission(permission)?.third.toTristate()
        }

    override fun setPermission(ctx: Set<Context>, permission: String, value: Tristate): CompletableFuture<Boolean> =
        GlobalScope.future {
            transaction(DB) {
                subjectEntity.setPermissionValue(permission, value.toBoolean())
            }
        }

    override fun getPermissions(ctx: Set<Context>): Map<String, Boolean> =
        transaction(DB) {
            subjectEntity.permissions.associate { entity ->
                entity.permission to entity.value
            }
        }

    override fun getAllPermissions(): Map<Set<Context>, Map<String, Boolean>> {
        val map = hashMapOf<Set<Context>, Map<String, Boolean>>()
        map[emptySet()] = getPermissions(emptySet())
        return map
    }

    override fun clearPermissions(ctx: Set<Context>): CompletableFuture<Boolean> =
        GlobalScope.future {
            transaction(DB) {
                SubjectPermissionTable.deleteWhere {
                    (SubjectPermissionTable.subject eq id)
                }
                true
            }
        }

    override fun clearPermissions(): CompletableFuture<Boolean> = clearPermissions(emptySet())

    //
    // Parents
    //

    override fun isChildOf(ctx: Set<Context>, parent: SubjectReference): Boolean = transaction(DB) {
        val parentEntity = parent.toEntity() ?: return@transaction false
        val parents = subjectEntity.parents
        parents.any { it.id == parentEntity.id }
    }

    override fun addParent(ctx: Set<Context>, parent: SubjectReference): CompletableFuture<Boolean> =
        GlobalScope.future {
            transaction(DB) {
                val parentEntity = parent.toEntity() ?: return@transaction false
                val parents = subjectEntity.parents
                if (parentEntity !in parents) {
                    subjectEntity.parents = SizedCollection(parents + parentEntity)
                    true
                } else {
                    false
                }
            }
        }

    override fun removeParent(ctx: Set<Context>, parent: SubjectReference): CompletableFuture<Boolean> =
        GlobalScope.future {
            transaction(DB) {
                val parentEntity = parent.toEntity() ?: return@transaction false
                val parents = subjectEntity.parents
                if (parentEntity in parents) {
                    subjectEntity.parents = SizedCollection(parents - parentEntity)
                    true
                } else {
                    false
                }
            }
        }

    override fun getParents(ctx: Set<Context>): List<SubjectReference> =
        transaction(DB) {
            subjectEntity.parents.map {
                ArvenSubjectReference(it.collection.identifier, it.identifier)
            }
        }

    override fun getAllParents(): Map<Set<Context>, List<SubjectReference>> {
        val map = hashMapOf<Set<Context>, List<SubjectReference>>()
        map[emptySet()] = getParents(emptySet())
        return map
    }

    override fun clearParents(ctx: Set<Context>): CompletableFuture<Boolean> =
        GlobalScope.future {
            transaction(DB) {
                SubjectParentTable.deleteWhere {
                    (SubjectParentTable.child eq id)
                }
                true
            }
        }

    override fun clearParents(): CompletableFuture<Boolean> = clearParents(emptySet())

    //
    // Options
    //

    override fun getOption(ctx: Set<Context>, key: String): Optional<String> =
        transaction(DB) {
            SubjectOptionEntity.find {
                (SubjectOptionTable.subject eq id) and (SubjectOptionTable.key eq key)
            }.singleOrNull()?.value.wrap()
        }

    override fun setOption(ctx: Set<Context>, key: String, value: String?): CompletableFuture<Boolean> =
        GlobalScope.future {
            transaction(DB) {
                val entity = SubjectOptionEntity.find {
                    (SubjectOptionTable.subject eq id) and (SubjectOptionTable.key eq key)
                }.singleOrNull()

                when {
                    entity == null && value != null -> {
                        SubjectOptionEntity.new {
                            this.subject = subjectEntity
                            this.key = key
                            this.value = value
                        }
                        true
                    }
                    entity != null && value != null -> {
                        entity.value = value
                        true
                    }
                    entity != null && value == null -> {
                        entity.delete()
                        true
                    }
                    else                            -> false
                }
            }
        }

    override fun getOptions(ctx: Set<Context>): Map<String, String> =
        transaction(DB) {
            val entities = SubjectOptionEntity.find {
                (SubjectOptionTable.subject eq id)
            }
            val map = hashMapOf<String, String>()

            for (entity in entities) {
                map[entity.key] = entity.value
            }

            map
        }

    override fun getAllOptions(): Map<Set<Context>, Map<String, String>> {
        val map = hashMapOf<Set<Context>, Map<String, String>>()
        map[emptySet()] = getOptions(emptySet())
        return map
    }

    override fun clearOptions(ctx: Set<Context>): CompletableFuture<Boolean> =
        GlobalScope.future {
            transaction(DB) {
                SubjectOptionTable.deleteWhere {
                    (SubjectOptionTable.subject eq id)
                }
                true
            }
        }

    override fun clearOptions(): CompletableFuture<Boolean> = clearOptions(emptySet())

    override fun getActiveContexts(): Set<Context> = emptySet()

    override fun getTransientSubjectData(): SubjectData = this

    override fun getSubjectData(): SubjectData = this

    override fun isSubjectDataPersisted(): Boolean = true
}
