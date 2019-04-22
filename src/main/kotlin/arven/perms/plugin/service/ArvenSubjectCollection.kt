package arven.perms.plugin.service

import arven.perms.plugin.ArvenPerms.Companion.DB
import arven.perms.plugin.database.*
import arven.perms.plugin.util.future
import arven.perms.plugin.util.isNotEmpty
import com.google.common.base.Predicates
import frontier.ske.java.util.wrap
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.service.context.Context
import org.spongepowered.api.service.permission.Subject
import org.spongepowered.api.service.permission.SubjectCollection
import org.spongepowered.api.service.permission.SubjectReference
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.function.Predicate
import kotlin.collections.HashSet

class ArvenSubjectCollection(private val id: EntityID<Int>, private val identifier: String) : SubjectCollection {

    private val entity: SubjectCollectionEntity
        get() = SubjectCollectionEntity[id]

    override fun getIdentifier(): String = identifier

    override fun newSubjectReference(subjectIdentifier: String): SubjectReference =
        ArvenSubjectReference(identifier, subjectIdentifier)

    override fun getIdentifierValidityPredicate(): Predicate<String> = Predicates.alwaysTrue()

    override fun getAllWithPermission(permission: String): CompletableFuture<Map<SubjectReference, Boolean>> =
        getAllWithPermission(emptySet(), permission)

    override fun getAllWithPermission(ctx: Set<Context>,
                                      permission: String): CompletableFuture<Map<SubjectReference, Boolean>> =
        transaction(DB) {
            val map = hashMapOf<SubjectReference, Boolean>()

            val rows = SubjectTable.innerJoin(SubjectCollectionTable).innerJoin(SubjectPermissionTable)
                .slice(SubjectTable.identifier, SubjectCollectionTable.identifier, SubjectPermissionTable.value)
                .select {
                    (SubjectTable.collection eq id) and
                            (SubjectTable.id eq SubjectPermissionTable.subject) and
                            (SubjectPermissionTable.permission eq permission)
                }

            for (row in rows) {
                val reference = ArvenSubjectReference(
                    row[SubjectCollectionTable.identifier],
                    row[SubjectTable.identifier]
                )
                map[reference] = row[SubjectPermissionTable.value]
            }

            map.future
        }

    override fun getLoadedWithPermission(permission: String): Map<Subject, Boolean> =
        getLoadedWithPermission(emptySet(), permission)

    override fun getLoadedWithPermission(ctx: Set<Context>, permission: String): Map<Subject, Boolean> =
        transaction(DB) {
            val map = hashMapOf<Subject, Boolean>()

            val rows = SubjectTable.innerJoin(SubjectPermissionTable)
                .slice(SubjectTable.id, SubjectTable.identifier, SubjectPermissionTable.value)
                .select {
                    (SubjectTable.id eq SubjectPermissionTable.subject) and
                            (SubjectPermissionTable.permission eq permission)
                }

            for (row in rows) {
                val subject = ArvenSubject(
                    this@ArvenSubjectCollection,
                    row[SubjectTable.id],
                    row[SubjectTable.identifier]
                )
                map[subject] = row[SubjectPermissionTable.value]
            }

            map
        }

    override fun hasSubject(identifier: String): CompletableFuture<Boolean> =
        transaction(DB) {
            SubjectTable
                .select { (SubjectTable.collection eq id) and (SubjectTable.identifier eq identifier) }
                .isNotEmpty
                .future
        }

    override fun getSubject(identifier: String): Optional<Subject> =
        transaction(DB) {
            SubjectEntity.find(id, identifier)
                ?.let { ArvenSubject(this@ArvenSubjectCollection, it.id, it.identifier) }
                .wrap()
        }

    override fun loadSubject(identifier: String): CompletableFuture<Subject> {
        return getOrCreateSubject(identifier).future
    }

    override fun loadSubjects(identifiers: Set<String>): CompletableFuture<Map<String, Subject>> =
        transaction(DB) {
            val map = hashMapOf<String, Subject>()

            for (identifier in identifiers) {
                map[identifier] = getOrCreateSubject(identifier)
            }

            map.future
        }

    override fun getLoadedSubjects(): Collection<Subject> =
        transaction(DB) {
            SubjectEntity
                .find { (SubjectTable.collection eq id) }
                .map { ArvenSubject(this@ArvenSubjectCollection, it.id, it.identifier) }
        }

    override fun getAllIdentifiers(): CompletableFuture<Set<String>> =
        transaction(DB) {
            SubjectTable
                .slice(SubjectTable.identifier)
                .select { SubjectTable.collection eq id }
                .mapTo(HashSet()) { it[SubjectTable.identifier] }
                .future
        }

    override fun getDefaults(): Subject = getOrCreateSubject("default")

    /**
     * NOOP
     */
    override fun suggestUnload(identifier: String) = Unit

    internal fun getOrCreateSubject(identifier: String): Subject = transaction(DB) {
        val entity = SubjectEntity.find(id, identifier)
            ?: SubjectEntity.new {
                this.identifier = identifier
                this.collection = entity
                this.displayName = ""
            }
        ArvenSubject(this@ArvenSubjectCollection, entity.id, entity.identifier)
    }
}