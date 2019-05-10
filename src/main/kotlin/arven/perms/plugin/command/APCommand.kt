package arven.perms.plugin.command

import arven.perms.plugin.command.value.ArvenValueParameters.subjectEntityByDisplayNameOf
import arven.perms.plugin.command.value.ArvenValueParameters.subjectEntityOf
import arven.perms.plugin.database.SubjectCollectionEntity
import arven.perms.plugin.database.SubjectEntity
import arven.perms.plugin.database.SubjectOptionTable
import arven.perms.plugin.database.SubjectPermissionTable
import arven.perms.plugin.util.UI
import arven.perms.plugin.util.transactional
import frontier.ske.commandManager
import frontier.ske.service.pagination.contents
import frontier.ske.service.pagination.padding
import frontier.ske.service.pagination.pagination
import frontier.ske.service.pagination.title
import frontier.ske.text.not
import frontier.ske.text.plus
import frontier.ske.text.unaryPlus
import frontier.skpc.CommandTree
import frontier.skpc.util.component1
import frontier.skpc.util.component2
import frontier.skpc.util.component3
import frontier.skpc.util.component4
import frontier.skpc.value.standard.ValueParameters.boolean
import frontier.skpc.value.standard.ValueParameters.string
import frontier.skpc.value.standard.optionalOr
import org.jetbrains.exposed.sql.SizedCollection
import org.jetbrains.exposed.sql.SortOrder
import org.spongepowered.api.service.permission.PermissionService.SUBJECTS_GROUP
import org.spongepowered.api.service.permission.PermissionService.SUBJECTS_USER
import org.spongepowered.api.text.Text

object APCommand {

    fun register(plugin: Any) {
        val command = CommandTree.Root("ap", "aperms").apply {
            "groups" expand {

                "list" execute transactional { src ->
                    val groups = SubjectCollectionEntity[SUBJECTS_GROUP].subjects.map {
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
                }
            }

            "group" expand {
                subjectEntityOf(SUBJECTS_GROUP)("group") expand {

                    "delete" execute transactional { (group, src) ->
                        val name = group.identifier

                        group.delete()
                        src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6delete")
                    }

                    "info" execute transactional { (group, src) ->
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
                    }

                    "options" expand {

                        "clear" execute transactional { (group, src) ->
                            val name = group.identifier

                            group.clearOptions()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6options &8<- &6clear")
                        }

                        "list" execute transactional { (group, src) ->
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
                        }
                    }

                    "option" / string("key") expand {

                        "check" execute transactional { (key, group, src) ->
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
                        }

                        "define" / string("value") execute transactional { (value, key, group, src) ->
                            if (group.setOptionValue(key, value)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e${group.identifier} &8<- &6option &e$key &8<- &6define &e$value")
                            } else {
                                src.sendMessage(+"&cFailed to set the option $key to $value &cfor group ${group.identifier}.")
                            }
                        }

                        "undefine" execute transactional { (key, group, src) ->
                            if (group.setOptionValue(key, null)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e${group.identifier} &8<- &6option &e$key &8<- &6undefine")
                            } else {
                                src.sendMessage(+"&cFailed to unset the option $key &cfor group ${group.identifier}.")
                            }
                        }
                    }

                    "parents" expand {

                        "clear" execute transactional { (group, src) ->
                            val name = group.identifier

                            group.clearParents()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6parents &8<- &6clear")
                        }

                        "list" execute transactional { (group, src) ->
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
                        }
                    }

                    "parent" / subjectEntityOf(SUBJECTS_GROUP)("parentGroup") expand {

                        "add" execute transactional { (parent, group, src) ->
                            val parentName = parent.identifier
                            val name = group.identifier

                            group.parents = SizedCollection(group.parents + parent)
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6parent &e$parentName &8<- &6add")
                        }

                        "remove" execute transactional { (parent, group, src) ->
                            val parentName = parent.identifier
                            val name = group.identifier

                            group.parents = SizedCollection(group.parents - parent)
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6parent &e$parentName &8<- &6remove")
                        }
                    }

                    "permissions" expand {

                        "clear" execute transactional { (group, src) ->
                            val name = group.identifier

                            group.clearPermissions()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6permissions &8<- &6clear")
                        }

                        "list" execute transactional { (group, src) ->
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
                        }
                    }

                    "permission" / string("permission") expand {

                        "check" execute transactional { (permission, group, src) ->
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
                        }

                        "define" / boolean.optionalOr(true)("value") execute transactional { (value, permission, group, src) ->
                            val display = when (value) {
                                true  -> "&atrue"
                                false -> "&cfalse"
                            }

                            if (group.setPermissionValue(permission, value)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e${group.identifier} &8<- &6permission &e$permission &8<- &6define $display")
                            } else {
                                src.sendMessage(+"&cFailed to set the permission $permission to $value for group ${group.identifier}.")
                            }
                        }

                        "undefine" execute transactional { (permission, group, src) ->
                            if (group.setPermissionValue(permission, null)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e${group.identifier} &8<- &6permission &e$permission &8<- &6undefine")
                            } else {
                                src.sendMessage(+"&cFailed to unset the permission $permission for group ${group.identifier}.")
                            }
                        }
                    }
                }

                string("name") / "create" execute transactional { (name, src) ->
                    SubjectEntity.new {
                        this.identifier = name
                        this.collection = SubjectCollectionEntity[SUBJECTS_GROUP]
                        this.displayName = null
                        this.weight = 0
                    }

                    src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6group &e$name &8<- &6create")
                }
            }

            "users" expand {

                "list" execute transactional { src ->
                    val users = SubjectCollectionEntity[SUBJECTS_USER].subjects.map {
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
                }
            }

            "user" expand {
                subjectEntityByDisplayNameOf(SUBJECTS_USER)("user") expand {

                    "info" execute transactional { (user, src) ->
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
                    }

                    "options" expand {
                        "clear" execute transactional { (user, src) ->
                            user.clearOptions()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6options &8<- &6clear")
                        }

                        "list" execute transactional { (user, src) ->
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
                        }
                    }

                    "option" / string("key") expand {

                        "check" execute transactional { (key, user, src) ->
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
                        }

                        "define" / string("value") execute transactional { (value, key, user, src) ->
                            if (user.setOptionValue(key, value)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6option &e$key &8<- &6define &e$value")
                            } else {
                                src.sendMessage(+"&cFailed to set the option $key to $value &cfor user ${user.displayName}.")
                            }
                        }

                        "undefine" execute transactional { (key, user, src) ->
                            if (user.setOptionValue(key, null)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6option &e$key &8<- &6undefine")
                            } else {
                                src.sendMessage(+"&cFailed to unset the option $key &cfor user ${user.displayName}.")
                            }
                        }
                    }

                    "parents" expand {

                        "clear" execute transactional { (user, src) ->
                            user.clearParents()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6parents &8<- &6clear")
                        }

                        "list" execute transactional { (user, src) ->
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
                        }
                    }

                    "parent" / subjectEntityOf(SUBJECTS_GROUP)("group") expand {

                        "add" execute transactional { (parent, user, src) ->
                            user.parents = SizedCollection(user.parents + parent)
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6parent &e${parent.identifier} &8<- &6add")
                        }

                        "remove" execute transactional { (parent, user, src) ->
                            user.parents = SizedCollection(user.parents - parent)
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6parent &e${parent.identifier} &8<- &6remove")
                        }
                    }

                    "permissions" expand {

                        "clear" execute transactional { (user, src) ->
                            user.clearPermissions()
                            src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6permissions &8<- &6clear")
                        }

                        "list" execute transactional { (user, src) ->
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
                        }
                    }

                    "permission" / string("permission") expand {

                        "check" execute transactional { (permission, user, src) ->
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
                        }

                        "define" / boolean.optionalOr(true)("value") execute transactional { (value, permission, user, src) ->
                            val display = when (value) {
                                true  -> "&atrue"
                                false -> "&cfalse"
                            }

                            if (user.setPermissionValue(permission, value)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6permission &e$permission &8<- &6define $display")
                            } else {
                                src.sendMessage(+"&cFailed to set the permission $permission to $value for user ${user.displayName}.")
                            }
                        }

                        "undefine" execute transactional { (permission, user, src) ->
                            if (user.setPermissionValue(permission, null)) {
                                src.sendMessage(+"&2A&aPerms &8(&b${src.name}&8) | &6user &e${user.displayName} &8<- &6permission &e$permission &8<- &6undefine")
                            } else {
                                src.sendMessage(+"&cFailed to unset the permission $permission for user ${user.displayName}.")
                            }
                        }
                    }
                }
            }
        }

        commandManager.register(plugin, command.toCallable(), command.aliases)
    }
}