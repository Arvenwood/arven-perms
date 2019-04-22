package arven.perms.plugin.command

import arven.perms.plugin.ArvenPerms.Companion.DB
import arven.perms.plugin.command.annotation.CollectionKey
import arven.perms.plugin.command.annotation.OnlyCollection
import arven.perms.plugin.database.*
import arven.perms.plugin.util.UI
import arven.perms.plugin.util.plusAssign
import frontier.skc.annotation.Command
import frontier.skc.annotation.Permission
import frontier.skc.annotation.Source
import frontier.ske.service.pagination.padding
import frontier.ske.service.pagination.pagination
import frontier.ske.service.pagination.title
import frontier.ske.text.darkGray
import frontier.ske.text.not
import frontier.ske.text.plus
import frontier.ske.text.unaryPlus
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import org.spongepowered.api.command.CommandException
import org.spongepowered.api.command.CommandSource
import org.spongepowered.api.service.permission.PermissionService.SUBJECTS_GROUP
import org.spongepowered.api.text.Text

@Command("ap", "aperms")
object CommandAP {

    @Command("group")
    object SubGroup {

        @Command("create")
        fun create(@Source src: CommandSource, name: String) {
            transaction(DB) {
                val collection = SubjectCollectionEntity.getOrCreate(SUBJECTS_GROUP)
                val subject = SubjectEntity.find(collection.id, name)

                if (subject != null) {
                    throw CommandException(!"That group already exists!")
                }

                SubjectEntity.new {
                    this.identifier = name
                    this.collection = collection
                    this.displayName = null
                    this.weight = 0
                }

                src.sendMessage(+"&aCreated a new group named &f$name&a.")
            }
        }

        @Command("delete")
        fun delete(@Source src: CommandSource,
                   @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity) {
            transaction(DB) {
                val name = group.effectiveName
                group.delete()
                src.sendMessage(+"&aDeleted the group named &f$name&a.")
            }
        }

        @Command("info")
        fun info(@Source src: CommandSource,
                 @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity) {
            transaction(DB) {
                val permissionCount = group.permissions.count()
                val optionCount = group.options.count()
                val parentCount = group.parents.count()

                pagination(src) {
                    this.title = +"&6Group: &e${group.effectiveName}"
                    this.padding = +"&8="
                    this.contents(
                        +"&2Weight&8: &a${group.weight}",
                        +"&2Permissions &8(&a$permissionCount&8): " + UI.Button.view(
                            "/ap group perm list ${group.identifier}",
                            +"&bClick to view this group's permissions."
                        ),
                        +"&2Options &8(&a$optionCount&8): " + UI.Button.view(
                            "/ap group option list ${group.identifier}",
                            +"&bClick to view this group's options."
                        ),
                        +"&2Parents &8(&a$parentCount&8): " + UI.Button.view(
                            "/ap group parent list ${group.identifier}",
                            +"&bClick to view this group's parents."
                        )
                    )
                }
            }
        }

        @Command("weight")
        fun weight(@Source src: CommandSource,
                   @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity,
                   weight: Int) {
            transaction(DB) {
                group.weight = weight
                src.sendMessage(+"&2Set the weight for group &f$group &ato &f$weight&a.")
            }
        }

        @Command("parent")
        object SubParent {

            @Command("add")
            fun add(@Source src: CommandSource,
                    @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity,
                    @OnlyCollection(SUBJECTS_GROUP) parent: SubjectEntity) {
                transaction(DB) {
                    val groupName = group.effectiveName
                    val parentName = parent.effectiveName

                    if (group in parent.inheritanceList) {
                        throw CommandException(!"Adding $parentName as a parent for $groupName would cause a cycle!")
                    }
                    if (parent in group.parents) {
                        throw CommandException(!"$parentName is already a parent of $groupName!")
                    }

                    group.parents = SizedCollection(group.parents + parent)
                    src.sendMessage(+"&aAdded group &f$parentName &aas a parent of group &f$groupName&a.")
                }
            }

            @Command("clear")
            fun clear(@Source src: CommandSource,
                      @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity) {
                transaction(DB) {
                    group.parents = SizedCollection()
                    src.sendMessage(+"&aCleared all parents for group &f${group.effectiveName}&a.")
                }
            }

            @Command("list")
            fun list(@Source src: CommandSource,
                     @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity) {
                transaction(DB) {
                    val parents = group.parents
                        .map {
                            val childId = group.identifier
                            val parentId = it.identifier

                            +"&b${it.effectiveName} &8(&2Weight&8: &a${it.weight}&8) " + UI.Button.delete(
                                "/ap group parent remove $childId $parentId",
                                +"&cClick to remove this parent."
                            )
                        }

                    pagination(src) {
                        this.title = +"&6Group Parents: &e${group.effectiveName}"
                        this.padding = +"&8="
                        this.contents(parents)
                    }
                }
            }

            @Command("remove")
            fun remove(@Source src: CommandSource,
                       @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity,
                       @OnlyCollection(SUBJECTS_GROUP) parent: SubjectEntity) {
                transaction(DB) {
                    if (parent !in group.parents) {
                        throw CommandException(!"Group ${parent.effectiveName} is not a parent of group ${group.effectiveName}!")
                    }

                    SubjectParentTable.deleteWhere {
                        (SubjectParentTable.child eq group.id) and
                                (SubjectParentTable.parent eq parent.id)
                    }
                    src.sendMessage(+"&aRemoved group &f${parent.effectiveName} &aas a parent of &f${group.effectiveName}&a.")
                }
            }
        }

        @Command("perm")
        object SubPerm {

            @Command("check")
            fun check(@Source src: CommandSource,
                      @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity,
                      permission: String) {
                transaction(DB) {
                    val deep = SubjectPermissionEntity.getValueDeep(group, permission)
                    val defined = when (deep?.third) {
                        true  -> "true"
                        false -> "false"
                        null  -> "undefined"
                    }

                    val builder = Text.builder()
                        .append(+"&aPermission &f$permission &ais &f$defined &afor &f${group.effectiveName}&a.")

                    if (deep != null) {
                        if (deep.first.id != group.id) {
                            builder.append(+"\n&8-> &afrom subject &f${deep.first.effectiveName} &a(collection: &f${deep.first.collection.identifier}&a)")
                        }
                        if (deep.second != permission) {
                            builder.append(+"\n&8-> &aimplicitly granted by &f${deep.second}")
                        }
                    }

                    src.sendMessage(builder.build())
                }
            }

            @Command("clear")
            fun clear(@Source src: CommandSource,
                      @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity) {
                transaction(DB) {
                    SubjectPermissionTable.deleteWhere {
                        (SubjectPermissionTable.subject eq group.id)
                    }
                    src.sendMessage(+"&aCleared all permissions for group &f${group.effectiveName}&a.")
                }
            }

            @Command("define")
            fun define(@Source src: CommandSource,
                       @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity,
                       permission: String,
                       value: Boolean = true) {
                transaction(DB) {
                    if (SubjectPermissionEntity.setPermission(group, permission, value)) {
                        src.sendMessage(+"&aSet &f$permission &ato &f$value &afor group &f${group.effectiveName}&a.")
                    } else {
                        throw CommandException(!"Failed to set permission $permission for group ${group.effectiveName}")
                    }
                }
            }

            @Command("list")
            fun list(@Source src: CommandSource,
                     @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity) {
                transaction(DB) {
                    val permissions = group.permissions
                        .orderBy(SubjectPermissionTable.permission to SortOrder.ASC)
                        .map {
                            val value = when (it.value) {
                                true  -> "&atrue"
                                false -> "&cfalse"
                            }
                            +"&b${it.permission} $value " + UI.Button.delete(
                                "/ap group perm undefine ${group.identifier} ${it.permission}",
                                +"&cClick to undefine this permission."
                            )
                        }

                    pagination(src) {
                        this.title = +"&6Group Permissions: &e${group.effectiveName}"
                        this.padding = +"&8="
                        this.contents(permissions)
                    }
                }
            }

            @Command("undefine")
            fun undefine(@Source src: CommandSource,
                         @OnlyCollection(SUBJECTS_GROUP) group: SubjectEntity,
                         permission: String) {
                if (SubjectPermissionEntity.setPermission(group, permission, null)) {
                    src.sendMessage(+"&aUnset &f$permission &afor group &f${group.effectiveName}&a.")
                } else {
                    throw CommandException(!"That permission is not set.")
                }
            }
        }
    }

    //
    // Advanced Commands
    //

    @Command("collection")
    object SubCollection {

        @Command("create")
        @Permission("arven.perms.collection.create")
        fun create(@Source src: CommandSource, name: String) {
            transaction(DB) {
                val collection = SubjectCollectionEntity.find(name)

                if (collection != null) {
                    throw CommandException(!"That subject collection already exists.")
                }

                SubjectCollectionEntity.new {
                    this.identifier = name
                }

                src.sendMessage(+"&aCreated a new subject collection named &f$name&a.")
            }
        }

        @Command("delete")
        fun delete(@Source src: CommandSource, collection: SubjectCollectionEntity) {
            transaction(DB) {
                val name = collection.identifier
                collection.delete()
                src.sendMessage(+"&aDeleted a subject collection named &f$name&a.")
            }
        }

        @Command("info")
        fun info(@Source src: CommandSource, collection: SubjectCollectionEntity) {
            transaction(DB) {
                val collectionName = collection.identifier
                val subjectCount = collection.subjects.count()

                pagination(src) {
                    this.title = +"&6Subject Collection: &e$collectionName"
                    this.padding = "=".darkGray()
                    this.contents(
                        +"&2Subjects &8(&a$subjectCount&8): " + UI.Button.view(
                            "/ap collection subjects $collectionName",
                            +"&bClick to view this collection's subjects."
                        )
                    )
                }
            }
        }

        @Command("list")
        fun list(@Source src: CommandSource) {
            transaction(DB) {
                val collections = SubjectCollectionEntity.all()
                    .map {
                        val subjectCount = it.subjects.count()
                        val edit = UI.Button.view(
                            "/ap collection info ${it.identifier}",
                            +"&bClick to view this subject collection."
                        )
                        val delete = UI.Button.delete(
                            "/ap collection delete ${it.identifier}",
                            +"&cClick to delete this subject collection."
                        )

                        +"&a${it.identifier} &8(&2Size&8: &a$subjectCount&8) " + edit + " " + delete
                    }

                pagination(src) {
                    this.title = +"&6Subject Collections"
                    this.padding = +"&8="
                    this.contents(collections)
                }
            }
        }

        @Command("subjects")
        fun subjects(@Source src: CommandSource, collection: SubjectCollectionEntity) {
            transaction(DB) {
                val subjects = collection.subjects
                    .map {
                        val builder = Text.builder()
                        builder += +"&a${it.identifier} "

                        if (it.displayName != null) {
                            builder += +"&8(&a${it.displayName}&8) "
                        }

                        builder += UI.Button.view(
                            "/ap subject info ${collection.identifier} ${it.identifier}",
                            +"&bClick to edit this subject."
                        )
                        builder += !" "
                        builder += UI.Button.delete(
                            "/ap subject delete ${collection.identifier} ${it.identifier}",
                            +"&cClick to delete this subject."
                        )

                        builder.build()
                    }

                pagination(src) {
                    this.title = +"&6Subjects in &e${collection.identifier}"
                    this.padding = +"&8="
                    this.contents(subjects)
                }
            }
        }
    }

    @Command("subject")
    object SubSubject {

        @Command("create")
        fun create(@Source src: CommandSource, collection: SubjectCollectionEntity, identifier: String) {
            transaction(DB) {
                val entity = SubjectEntity.find(collection.id, identifier)

                if (entity != null) {
                    throw CommandException(!"That subject already exists.")
                }

                SubjectEntity.new {
                    this.identifier = identifier
                    this.collection = collection
                    this.displayName = null
                }

                src.sendMessage(+"&aCreated a new subject named &f$identifier &ain &f${collection.identifier}&a.")
            }
        }

        @Command("delete")
        fun delete(@Source src: CommandSource, @CollectionKey subject: SubjectEntity) {
            transaction(DB) {
                val subjectName = subject.effectiveName
                subject.delete()
                src.sendMessage(+"&aDeleted a subject named &f$subjectName&a.")
            }
        }

        @Command("info")
        fun info(@Source src: CommandSource, @CollectionKey subject: SubjectEntity) {
            transaction(DB) {
                val subjectName = subject.identifier
                val collectionName = subject.collection.identifier

                val permissionCount = subject.permissions.count()
                val optionCount = subject.options.count()
                val parentCount = subject.parents.count()

                pagination(src) {
                    this.title = +"&6Subject: &e${subject.effectiveName} &6(&e$collectionName&6)"
                    this.padding = +"&8="
                    this.contents(
                        +"&2Permissions &8(&a$permissionCount&8): " + UI.Button.view(
                            "/ap subject perm list $collectionName $subjectName",
                            +"&bClick to view this subject's permissions."
                        ),
                        +"&2Options &8(&a$optionCount&8): " + UI.Button.view(
                            "/ap subject option list $collectionName $subjectName",
                            +"&bClick to view this subject's options."
                        ),
                        +"&2Parents &8(&a$parentCount&8): " + UI.Button.view(
                            "/ap subject parent list $collectionName $subjectName",
                            +"&bClick to view this subject's parents."
                        )
                    )
                }
            }
        }

        @Command("parent")
        object SubParent {

            @Command("add")
            fun add(@Source src: CommandSource,
                    @CollectionKey("childCollection") child: SubjectEntity,
                    @CollectionKey("parentCollection") parent: SubjectEntity) {
                transaction(DB) {
                    if (parent in child.parents) {
                        throw CommandException(!"${parent.effectiveName} is already a parent of ${child.effectiveName}.")
                    }

                    child.parents = SizedCollection(child.parents + parent)
                    src.sendMessage(+"&aAdded &f${parent.effectiveName} &aas a parent to &f${child.effectiveName}&a.")
                }
            }

            @Command("list")
            fun list(@Source src: CommandSource, @CollectionKey subject: SubjectEntity) {
                transaction(DB) {
                    val parents = subject.parents
                        .map {
                            val childName = subject.identifier
                            val childCollName = subject.collection.identifier
                            val parentName = it.identifier
                            val parentCollName = it.collection.identifier

                            +"&b${it.effectiveName} &8(&2Weight&8: &a${it.weight}&8) " + UI.Button.delete(
                                "/ap subject parent remove $childCollName $childName $parentCollName $parentName"
                                        + "&cClick to remove this parent."
                            )
                        }

                    pagination(src) {
                        this.title = +"&6Subject Parents: &e${subject.effectiveName}"
                        this.padding = +"&8="
                        this.contents(parents)
                    }
                }
            }

            @Command("remove")
            fun remove(@Source src: CommandSource,
                       @CollectionKey("childCollection") child: SubjectEntity,
                       @CollectionKey("parentCollection") parent: SubjectEntity) {
                transaction(DB) {
                    if (parent !in child.parents) {
                        throw CommandException(!"${parent.effectiveName} is not a parent of ${child.effectiveName}.")
                    }

                    child.parents = SizedCollection(child.parents - parent)
                    src.sendMessage(+"&aRemoved &f${parent.effectiveName} &aas a parent of &f${child.effectiveName}&a.")
                }
            }
        }

        @Command("perm")
        object SubPerm {

            @Command("check")
            fun check(@Source src: CommandSource, @CollectionKey subject: SubjectEntity, permission: String) {
                transaction(DB) {
                    val subjectName = subject.effectiveName

                    val deep = SubjectPermissionEntity.getValueDeep(subject, permission)
                    val defined = when (deep?.third) {
                        true  -> "true"
                        false -> "false"
                        null  -> "undefined"
                    }
                    src.sendMessage(+"&aPermission &f$permission &ais &f$defined &afor &f$subjectName&a.")
                }
            }

            @Command("clear")
            fun clear(@Source src: CommandSource, @CollectionKey subject: SubjectEntity) {
                transaction(DB) {
                    val subjectName = subject.effectiveName

                    SubjectPermissionTable.deleteWhere {
                        (SubjectPermissionTable.subject eq subject.id)
                    }
                    src.sendMessage(+"&aCleared all permissions for &f$subjectName&a.")
                }
            }

            @Command("define")
            fun define(@Source src: CommandSource, @CollectionKey subject: SubjectEntity,
                       permission: String, value: Boolean = true) {
                transaction(DB) {
                    val subjectName = subject.effectiveName

                    if (SubjectPermissionEntity.setPermission(subject, permission, value)) {
                        src.sendMessage(+"&aSet &f$permission &ato &f$value &afor &f$subjectName&a.")
                    } else {
                        throw CommandException(!"Failed to set permission $permission for subject $subjectName.")
                    }
                }
            }

            @Command("list")
            fun list(@Source src: CommandSource, @CollectionKey subject: SubjectEntity) {
                transaction(DB) {
                    val subjectName = subject.identifier
                    val collectionName = subject.collection.identifier

                    val permissions = subject.permissions
                        .orderBy(SubjectPermissionTable.permission to SortOrder.ASC)
                        .map {
                            val value = when (it.value) {
                                true  -> "&atrue"
                                false -> "&cfalse"
                            }
                            +"&b${it.permission} $value " + UI.Button.delete(
                                "/ap subject perm undefine $collectionName $subjectName ${it.permission}",
                                +"&cClick to undefine this permission."
                            )
                        }

                    pagination(src) {
                        this.title = +"&6Subject Permissions: &e${subject.effectiveName}"
                        this.padding = +"&8="
                        this.contents(permissions)
                    }
                }
            }

            @Command("undefine")
            fun define(@Source src: CommandSource, @CollectionKey subject: SubjectEntity, permission: String) {
                transaction(DB) {
                    val subjectName = subject.effectiveName

                    if (SubjectPermissionEntity.setPermission(subject, permission, null)) {
                        src.sendMessage(+"&aUnset &f$permission &afor &f$subjectName&a.")
                    } else {
                        throw CommandException(!"That permission is not set.")
                    }
                }
            }
        }
    }
}