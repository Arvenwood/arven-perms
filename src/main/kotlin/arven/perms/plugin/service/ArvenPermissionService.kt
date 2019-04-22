package arven.perms.plugin.service

import arven.perms.plugin.ArvenPerms.Companion.DB
import arven.perms.plugin.database.SubjectCollectionEntity
import arven.perms.plugin.database.SubjectCollectionTable
import arven.perms.plugin.util.future
import arven.perms.plugin.util.isNotEmpty
import arven.perms.plugin.util.toTristate
import com.google.common.base.Predicates
import frontier.ske.java.util.unwrap
import frontier.ske.java.util.wrap
import frontier.ske.plugin.toPluginContainer
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.plugin.PluginContainer
import org.spongepowered.api.service.context.ContextCalculator
import org.spongepowered.api.service.permission.*
import org.spongepowered.api.text.Text
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import kotlin.collections.HashSet

class ArvenPermissionService : PermissionService {

    private val descriptions = hashMapOf<String, PermissionDescription>()

    override fun newSubjectReference(collectionIdentifier: String, subjectIdentifier: String): SubjectReference =
        ArvenSubjectReference(collectionIdentifier, subjectIdentifier)

    override fun hasCollection(identifier: String): CompletableFuture<Boolean> =
        SubjectCollectionTable.select {
            (SubjectCollectionTable.identifier eq identifier)
        }.isNotEmpty.future

    override fun getUserSubjects(): SubjectCollection {
        return getOrCreateCollection(PermissionService.SUBJECTS_USER)
    }

    override fun getGroupSubjects(): SubjectCollection {
        return getOrCreateCollection(PermissionService.SUBJECTS_GROUP)
    }

    override fun getCollection(identifier: String): Optional<SubjectCollection> = transaction(DB) {
        SubjectCollectionEntity
            .find(identifier)
            ?.let { ArvenSubjectCollection(it.id, it.identifier) }
            .wrap()
    }

    override fun loadCollection(identifier: String): CompletableFuture<SubjectCollection> {
        return getOrCreateCollection(identifier).future
    }

    override fun getLoadedCollections(): Map<String, SubjectCollection> = transaction(DB) {
        val map = hashMapOf<String, SubjectCollection>()
        for (entity in SubjectCollectionEntity.all()) {
            map[entity.identifier] = ArvenSubjectCollection(entity.id, entity.identifier)
        }
        map
    }

    override fun getAllIdentifiers(): CompletableFuture<Set<String>> = transaction(DB) {
        SubjectCollectionTable
            .slice(SubjectCollectionTable.identifier)
            .selectAll()
            .mapTo(HashSet()) { it[SubjectCollectionTable.identifier] }
            .future
    }

    override fun getDefaults(): Subject =
        getOrCreateCollection(PermissionService.SUBJECTS_DEFAULT).getOrCreateSubject("default")

    override fun newDescriptionBuilder(plugin: Any): PermissionDescription.Builder = ArvenDescriptionBuilder(plugin)

    override fun getDescription(permission: String): Optional<PermissionDescription> = descriptions[permission].wrap()

    override fun getDescriptions(): Collection<PermissionDescription> = descriptions.values

    /**
     * NOOP
     */
    override fun registerContextCalculator(calculator: ContextCalculator<Subject>) = Unit

    override fun getIdentifierValidityPredicate(): Predicate<String> = Predicates.alwaysTrue()

    private fun getOrCreateCollection(identifier: String): ArvenSubjectCollection = transaction(DB) {
        val entity = SubjectCollectionEntity.find(identifier)
            ?: SubjectCollectionEntity.new { this.identifier = identifier }
        ArvenSubjectCollection(entity.id, entity.identifier)
    }

    private inner class ArvenDescriptionBuilder(private val plugin: Any) : PermissionDescription.Builder {
        private lateinit var permission: String
        private var description: Text? = null
        private val assigned = hashMapOf<String, Boolean>()

        override fun id(permissionId: String): PermissionDescription.Builder {
            this.permission = permissionId
            return this
        }

        override fun description(description: Text?): PermissionDescription.Builder {
            this.description = description
            return this
        }

        override fun assign(role: String, value: Boolean): PermissionDescription.Builder {
            assigned[role] = value
            return this
        }

        override fun register(): PermissionDescription {
            val collection = getOrCreateCollection(PermissionService.SUBJECTS_ROLE_TEMPLATE)
            for ((role, value) in assigned) {
                collection.getOrCreateSubject(role)
                    .subjectData.setPermission(emptySet(), permission, value.toTristate())
            }
            return ArvenDescription(plugin.toPluginContainer(), permission, description)
        }
    }

    private inner class ArvenDescription(private val plugin: PluginContainer?,
                                         private val permission: String,
                                         private val description: Text?) : PermissionDescription {

        override fun getOwner(): Optional<PluginContainer> = plugin.wrap()

        override fun getId(): String = permission

        override fun getDescription(): Optional<Text> = description.wrap()

        override fun getAssignedSubjects(collectionIdentifier: String): Map<Subject, Boolean> =
            getCollection(collectionIdentifier).unwrap()?.getLoadedWithPermission(permission).orEmpty()

        override fun findAssignedSubjects(collectionIdentifier: String):
                CompletableFuture<Map<SubjectReference, Boolean>> {
            return loadCollection(collectionIdentifier)
                .thenCompose { it.getAllWithPermission(permission) }
        }
    }
}