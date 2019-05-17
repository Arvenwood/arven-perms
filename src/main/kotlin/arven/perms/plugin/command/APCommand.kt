package arven.perms.plugin.command

import arven.perms.plugin.command.value.ArvenValueParameters.subjectEntityByDisplayNameOf
import arven.perms.plugin.command.value.ArvenValueParameters.subjectEntityOf
import arven.perms.plugin.database.SubjectCollectionEntity
import arven.perms.plugin.database.SubjectEntity
import arven.perms.plugin.database.SubjectOptionTable
import arven.perms.plugin.database.SubjectPermissionTable
import arven.perms.plugin.util.UI
import arven.perms.plugin.util.unaryPlus
import frontier.ske.commandManager
import frontier.ske.service.contents
import frontier.ske.service.padding
import frontier.ske.service.pagination
import frontier.ske.service.title
import frontier.ske.text.not
import frontier.ske.text.plus
import frontier.skpc.CommandTree
import frontier.skpc.permission
import frontier.skpc.util.component1
import frontier.skpc.util.component2
import frontier.skpc.util.component3
import frontier.skpc.value.standard.ValueParameters.boolean
import frontier.skpc.value.standard.ValueParameters.string
import frontier.skpc.value.standard.optionalOr
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SortOrder
import org.spongepowered.api.command.CommandResult
import org.spongepowered.api.service.permission.PermissionService.SUBJECTS_GROUP
import org.spongepowered.api.service.permission.PermissionService.SUBJECTS_USER
import org.spongepowered.api.text.Text

object APCommand {

    fun register(plugin: Any) {
        val command = CommandTree.Root("ap", "aperms", permission = "arven.perms.base").apply {
            ("groups" permission "arven.perms.group.base") expand {

                ("list" permission "arven.perms.group.list") execute transactional { src, _ ->
                    val groups = SubjectCollectionEntity.getOrCreate(SUBJECTS_GROUP).subjects.map {
                        val builder = Text.builder()
                        builder.append(+"&a${it.identifier} ")

                        if (it.displayName != null) {
                            builder.append(+"&8(&a${it.displayName}&8) ")
                        }

                        builder.append(
                            UI.Button.view(
                                "/ap group ${it.identifier} info",
                                +"&bClick to view this group."
                            )
                        )

                        builder.append(!" ")

                        builder.append(
                            UI.Button.delete(
                                "/ap group ${it.identifier} delete",
                                +"&cClick to delete this group."
                            )
                        )

                        builder.build()
                    }

                    pagination(src) {
                        this.title = +"&6Groups"
                        this.padding = +"&8="
                        this.contents = groups
                    }

                    CommandResult.success()
                }
            }

            ("group" permission "arven.perms.group.base") expand {
                subjectEntityOf(SUBJECTS_GROUP)("group") expand {

                    ("delete" permission "arven.perms.group.delete") execute transactional { src, (group) ->
                        val name = group.identifier

                        group.delete()
                        src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6delete")

                        CommandResult.success()
                    }

                    ("info" permission "arven.perms.group.info") execute transactional { src, (group) ->
                        val permissionCount = group.permissions.count()
                        val optionCount = group.options.count()
                        val parentCount = group.parents.count()

                        pagination(src) {
                            this.title = +"&6Group: &e${group.identifier}"
                            this.padding = +"&8="
                            this.contents(
                                +"&2Weight&8: &a${group.weight}",
                                (+"&2Permissions &8(&a$permissionCount&8): ") + UI.Button.view(
                                    "/ap group ${group.identifier} permissions list",
                                    +"&bClick to view this group's permissions."
                                ),
                                (+"&2Options &8(&a$optionCount&8): ") + UI.Button.view(
                                    "/ap group ${group.identifier} options list",
                                    +"&bClick to view this group's options."
                                ),
                                (+"&2Parents &8(&a$parentCount&8): ") + UI.Button.view(
                                    "/ap group ${group.identifier} parents list",
                                    +"&bClick to view this group's parents."
                                )
                            )
                        }

                        CommandResult.success()
                    }

                    ("options" permission "arven.perms.group.option.base") expand {

                        ("clear" permission "arven.perms.group.option.clear") execute transactional { src, (group) ->
                            val name = group.identifier

                            group.clearOptions()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6options &8<- &6clear")

                            CommandResult.success()
                        }

                        ("list" permission "arven.perms.group.option.list") execute transactional { src, (group) ->
                            val options = group.options
                                .orderBy(SubjectOptionTable.key to SortOrder.ASC)
                                .map {
                                    (+"&a${it.key} &8= &b${it.value}") + UI.Button.delete(
                                        "/ap group ${group.identifier} option ${it.key} undefine",
                                        +"&cClick to undefine this option."
                                    )
                                }

                            pagination(src) {
                                this.title = +"&6Group Options: &e${group.identifier}"
                                this.padding = +"&8="
                                this.contents = options
                            }

                            CommandResult.success()
                        }
                    }

                    ("option" permission "arven.perms.group.option.base") / string("key") expand {

                        ("check" permission "arven.perms.group.option.check") execute transactional { src, (key, group) ->
                            val deep = group.getOption(key)
                            val value = when (deep?.second) {
                                null -> "&fundefined"
                                else -> "&e${deep.second}"
                            }

                            val builder = Text.builder()

                            builder.append(+"&2A&aPerms &8| &6group &e${group.identifier} &8<- &6option &e$key &8<- &6is $value")

                            if (deep != null) {
                                if (deep.first.id != group.id) {
                                    builder.append(+"\n    &8<- &6inherited from group &e${deep.first.identifier}")
                                }
                            }

                            src.sendMessage(builder.build())

                            CommandResult.success()
                        }

                        ("define" permission "arven.perms.group.option.define") / string("value") execute transactional { src, (value, key, group) ->
                            if (group.setOptionValue(key, value)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e${group.identifier} &8<- &6option &e$key &8<- &6define &e$value")
                            } else {
                                src.sendMessage(+"&cFailed to set the option $key to $value &cfor group ${group.identifier}.")
                            }

                            CommandResult.success()
                        }

                        ("undefine" permission "arven.perms.group.option.undefine") execute transactional { src, (key, group) ->
                            if (group.setOptionValue(key, null)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e${group.identifier} &8<- &6option &e$key &8<- &6undefine")
                            } else {
                                src.sendMessage(+"&cFailed to unset the option $key &cfor group ${group.identifier}.")
                            }

                            CommandResult.success()
                        }
                    }

                    ("parents" permission "arven.perms.group.parent.base") expand {

                        ("clear" permission "arven.perms.group.parent.clear") execute transactional { src, (group) ->
                            val name = group.identifier

                            group.clearParents()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6parents &8<- &6clear")

                            CommandResult.success()
                        }

                        ("list" permission "arven.perms.group.parent.list") execute transactional { src, (group) ->
                            val parents = group.parents.map {
                                val builder = Text.builder()

                                builder.append(+"&a${it.identifier} ")

                                if (it.displayName != null) {
                                    builder.append(+"&8(&a${it.displayName}&8) ")
                                }

                                builder.append(
                                    UI.Button.view(
                                        "/ap group ${it.identifier} info",
                                        +"&bClick to view this group."
                                    ),
                                    !" ",
                                    UI.Button.delete(
                                        "/ap group ${group.identifier} parent ${it.identifier} remove",
                                        +"&cClick to remove this group as a parent."
                                    )
                                )

                                builder.build()
                            }

                            pagination(src) {
                                this.title = +"&6Group Parents: &e${group.identifier}"
                                this.padding = +"&8="
                                this.contents = parents
                            }

                            CommandResult.success()
                        }
                    }

                    ("parent" permission "arven.perms.group.parent.base") / subjectEntityOf(SUBJECTS_GROUP)("parentGroup") expand {

                        ("add" permission "arven.perms.group.parent.add") execute transactional { src, (parent, group) ->
                            val parentName = parent.identifier
                            val name = group.identifier

                            group.parents = SizedCollection(group.parents + parent)
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6parent &e$parentName &8<- &6add")

                            CommandResult.success()
                        }

                        ("remove" permission "arven.perms.group.parent.remove") execute transactional { src, (parent, group) ->
                            val parentName = parent.identifier
                            val name = group.identifier

                            group.parents = SizedCollection(group.parents - parent)
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6parent &e$parentName &8<- &6remove")

                            CommandResult.success()
                        }
                    }

                    ("permissions" permission "arven.perms.group.permission.base") expand {

                        ("clear" permission "arven.perms.group.permission.clear") execute transactional { src, (group) ->
                            val name = group.identifier

                            group.clearPermissions()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6permissions &8<- &6clear")

                            CommandResult.success()
                        }

                        ("list" permission "arven.perms.group.permission.list") execute transactional { src, (group) ->
                            val permissions = group.permissions
                                .orderBy(SubjectPermissionTable.permission to SortOrder.ASC)
                                .map {
                                    val value = when (it.value) {
                                        true  -> "&atrue"
                                        false -> "&cfalse"
                                    }
                                    (+"&b${it.permission} $value ") + UI.Button.delete(
                                        "/ap group ${group.identifier} permission ${it.permission} undefine",
                                        +"&cClick to undefine this permission."
                                    )
                                }

                            pagination(src) {
                                this.title = +"&6Group Permissions: &e${group.identifier}"
                                this.padding = +"&8="
                                this.contents = permissions
                            }

                            CommandResult.success()
                        }
                    }

                    ("permission" permission "arven.perms.group.permission.base") / string("permission") expand {

                        ("check" permission "arven.perms.group.permission.check") execute transactional { src, (permission, group) ->
                            val deep = group.getPermission(permission)
                            val value = when (deep?.third) {
                                true  -> "&atrue"
                                false -> "&cfalse"
                                null  -> "&fundefined"
                            }

                            val builder = Text.builder()

                            builder.append(+"&2A&aPerms &8| &6group &e${group.identifier} &8<- &6permission &e$permission &8<- &6is $value")

                            if (deep != null) {
                                if (deep.first.id != group.id) {
                                    builder.append(+"\n    &8<- &6inherited from group &e${deep.first.identifier}")
                                }
                                if (deep.second != permission) {
                                    builder.append(+"\n    &8<- &6implicitly granted by &e${deep.second}")
                                }
                            }

                            src.sendMessage(builder.build())

                            CommandResult.success()
                        }

                        ("define" permission "arven.perms.group.permission.define") / boolean.optionalOr(true)("value") execute transactional { src, (value, permission, group) ->
                            val display = when (value) {
                                true  -> "&atrue"
                                false -> "&cfalse"
                            }

                            if (group.setPermissionValue(permission, value)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e${group.identifier} &8<- &6permission &e$permission &8<- &6define $display")
                            } else {
                                src.sendMessage(+"&cFailed to set the permission $permission to $value for group ${group.identifier}.")
                            }

                            CommandResult.success()
                        }

                        ("undefine" permission "arven.perms.group.permission.undefine") execute transactional { src, (permission, group) ->
                            if (group.setPermissionValue(permission, null)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e${group.identifier} &8<- &6permission &e$permission &8<- &6undefine")
                            } else {
                                src.sendMessage(+"&cFailed to unset the permission $permission for group ${group.identifier}.")
                            }

                            CommandResult.success()
                        }
                    }
                }

                string("name") / ("create" permission "arven.perms.group.create") execute transactional { src, (name) ->
                    val collectionEntity = SubjectCollectionEntity.getOrCreate(SUBJECTS_GROUP)

                    SubjectEntity.new {
                        this.identifier = name
                        this.collection = collectionEntity
                        this.displayName = null
                        this.weight = collectionEntity.defaultWeight
                    }

                    src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6create")

                    CommandResult.success()
                }
            }

            ("users" permission "arven.perms.user.base") expand {

                ("list" permission "arven.perms.user.list") execute transactional { src, _ ->
                    val users = SubjectCollectionEntity.getOrCreate(SUBJECTS_USER).subjects.map {
                        val builder = Text.builder()

                        builder.append(
                            +"&a${it.displayName} &8(&a${it.identifier}&8) ",
                            UI.Button.view(
                                "/ap user ${it.displayName} info",
                                +"&bClick to view this user."
                            ),
                            !" ",
                            UI.Button.delete(
                                "/ap user ${it.displayName} delete",
                                +"&cClick to delete this user."
                            )
                        )

                        builder.build()
                    }

                    pagination(src) {
                        this.title = +"&6Users"
                        this.padding = +"&8="
                        this.contents = users
                    }

                    CommandResult.success()
                }
            }

            ("user" permission "arven.perms.user.base") expand {
                subjectEntityByDisplayNameOf(SUBJECTS_USER)("user") expand {

                    ("info" permission "arven.perms.user.info") execute transactional { src, (user) ->
                        val permissionCount = user.permissions.count()
                        val optionCount = user.options.count()
                        val parentCount = user.parents.count()

                        pagination(src) {
                            this.title = +"&6User: &e${user.displayName}"
                            this.padding = +"&8="
                            this.contents(
                                (+"&2Permissions &8(&a$permissionCount&8): ") + UI.Button.view(
                                    "/ap user ${user.displayName} permissions list",
                                    +"&bClick to view this user's permissions."
                                ),
                                (+"&2Options &8(&a$optionCount&8): ") + UI.Button.view(
                                    "/ap user ${user.displayName} options list",
                                    +"&bClick to view this user's options."
                                ),
                                (+"&2Parents &8(&a$parentCount&8): ") + UI.Button.view(
                                    "/ap user ${user.displayName} parents list",
                                    +"&bClick to view this user's parents."
                                )
                            )
                        }

                        CommandResult.success()
                    }

                    ("options" permission "arven.perms.user.option.base") expand {
                        ("clear" permission "arven.perms.user.option.clear") execute transactional { src, (user) ->
                            user.clearOptions()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6options &8<- &6clear")

                            CommandResult.success()
                        }

                        ("list" permission "arven.perms.user.option.list") execute transactional { src, (user) ->
                            val options = user.options
                                .orderBy(SubjectOptionTable.key to SortOrder.ASC)
                                .map {
                                    (+"&a${it.key} &8= &b${it.value}") + UI.Button.delete(
                                        "/ap user ${user.displayName} option ${it.key} undefine",
                                        +"&cClick to undefine this option."
                                    )
                                }

                            pagination(src) {
                                this.title = +"&6User Options: &e${user.displayName}"
                                this.padding = +"&8="
                                this.contents = options
                            }

                            CommandResult.success()
                        }
                    }

                    ("option" permission "arven.perms.user.option.base") / string("key") expand {

                        ("check" permission "arven.perms.user.option.check") execute transactional { src, (key, user) ->
                            val deep = user.getOption(key)
                            val value = when (deep?.second) {
                                null -> "&fundefined"
                                else -> "&e${deep.second}"
                            }

                            val builder = Text.builder()

                            builder.append(+"&2A&aPerms &8| &6user &e${user.displayName} &8<- &6option &e$key &8<- &6is $value")

                            if (deep != null) {
                                if (deep.first.id != user.id) {
                                    builder.append(+"\n    &8<- &6inherited from group &e${deep.first.identifier}")
                                }
                            }

                            src.sendMessage(builder.build())

                            CommandResult.success()
                        }

                        ("define" permission "arven.perms.user.option.define") / string("value") execute transactional { src, (value, key, user) ->
                            if (user.setOptionValue(key, value)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6option &e$key &8<- &6define &e$value")
                            } else {
                                src.sendMessage(+"&cFailed to set the option $key to $value &cfor user ${user.displayName}.")
                            }

                            CommandResult.success()
                        }

                        ("undefine" permission "arven.perms.user.option.undefine") execute transactional { src, (key, user) ->
                            if (user.setOptionValue(key, null)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6option &e$key &8<- &6undefine")
                            } else {
                                src.sendMessage(+"&cFailed to unset the option $key &cfor user ${user.displayName}.")
                            }

                            CommandResult.success()
                        }
                    }

                    ("parents" permission "arven.perms.user.parent.base") expand {

                        ("clear" permission "arven.perms.user.parent.clear") execute transactional { src, (user) ->
                            user.clearParents()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6parents &8<- &6clear")

                            CommandResult.success()
                        }

                        ("list" permission "arven.perms.user.parent.list") execute transactional { src, (user) ->
                            val parents = user.parents.map {
                                val builder = Text.builder()

                                builder.append(+"&a${it.identifier} ")

                                if (it.displayName != null) {
                                    builder.append(+"&8(&a${it.displayName}&8) ")
                                }

                                builder.append(
                                    UI.Button.view(
                                        "/ap group ${it.identifier} info",
                                        +"&bClick to view this group."
                                    ),
                                    !" ",
                                    UI.Button.delete(
                                        "/ap user ${user.displayName} parent ${it.identifier} remove",
                                        +"&cClick to remove this group as a parent."
                                    )
                                )

                                builder.build()
                            }

                            pagination(src) {
                                this.title = +"&6User Parents: &e${user.displayName}"
                                this.padding = +"&8="
                                this.contents = parents
                            }

                            CommandResult.success()
                        }
                    }

                    ("parent" permission "arven.perms.user.parent.base") / subjectEntityOf(SUBJECTS_GROUP)("group") expand {

                        ("add" permission "arven.perms.user.parent.add") execute transactional { src, (parent, user) ->
                            user.parents = SizedCollection(user.parents + parent)
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6parent &e${parent.identifier} &8<- &6add")

                            CommandResult.success()
                        }

                        ("remove" permission "arven.perms.user.parent.remove") execute transactional { src, (parent, user) ->
                            user.parents = SizedCollection(user.parents - parent)
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6parent &e${parent.identifier} &8<- &6remove")

                            CommandResult.success()
                        }
                    }

                    ("permissions" permission "arven.perms.user.permission.base") expand {

                        ("clear" permission "arven.perms.user.permission.clear") execute transactional { src, (user) ->
                            user.clearPermissions()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6permissions &8<- &6clear")

                            CommandResult.success()
                        }

                        ("list" permission "arven.perms.user.permission.list") execute transactional { src, (user) ->
                            val permissions = user.permissions
                                .orderBy(SubjectPermissionTable.permission to SortOrder.ASC)
                                .map {
                                    val value = when (it.value) {
                                        true  -> "&atrue"
                                        false -> "&cfalse"
                                    }
                                    (+"&b${it.permission} $value ") + UI.Button.delete(
                                        "/ap user ${user.displayName} permission ${it.permission} undefine",
                                        +"&cClick to undefine this permission."
                                    )
                                }

                            pagination(src) {
                                this.title = +"&6User Permissions: &e${user.displayName}"
                                this.padding = +"&8="
                                this.contents = permissions
                            }

                            CommandResult.success()
                        }
                    }

                    ("permission" permission "arven.perms.user.permission.base") / string("permission") expand {

                        ("check" permission "arven.perms.user.permission.check") execute transactional { src, (permission, user) ->
                            val deep = user.getPermission(permission)
                            val value = when (deep?.third) {
                                true  -> "&atrue"
                                false -> "&cfalse"
                                null  -> "&fundefined"
                            }

                            val builder = Text.builder()

                            builder.append(+"&2A&aPerms &8| &6user &e${user.displayName} &8<- &6permission &e$permission &8<- &6is $value")

                            if (deep != null) {
                                if (deep.first.id != user.id) {
                                    builder.append(+"\n    &8<- &6inherited from group &e${deep.first.identifier}")
                                }
                                if (deep.second != permission) {
                                    builder.append(+"\n    &8<- &6implicitly granted by &e${deep.second}")
                                }
                            }

                            src.sendMessage(builder.build())

                            CommandResult.success()
                        }

                        ("define" permission "arven.perms.user.permission.define") / boolean.optionalOr(true)("value") execute transactional { src, (value, permission, user) ->
                            val display = when (value) {
                                true  -> "&atrue"
                                false -> "&cfalse"
                            }

                            if (user.setPermissionValue(permission, value)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6permission &e$permission &8<- &6define $display")
                            } else {
                                src.sendMessage(+"&cFailed to set the permission $permission to $value for user ${user.displayName}.")
                            }

                            CommandResult.success()
                        }

                        ("undefine" permission "arven.perms.user.permission.undefine") execute transactional { src, (permission, user) ->
                            if (user.setPermissionValue(permission, null)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6permission &e$permission &8<- &6undefine")
                            } else {
                                src.sendMessage(+"&cFailed to unset the permission $permission for user ${user.displayName}.")
                            }

                            CommandResult.success()
                        }
                    }
                }
            }
        }

        commandManager.register(plugin, command.toCallable(), command.aliases)
    }
}