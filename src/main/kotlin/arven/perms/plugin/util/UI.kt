package arven.perms.plugin.util

import frontier.ske.text.aqua
import frontier.ske.text.red
import frontier.ske.text.runCommand
import org.spongepowered.api.text.Text
import org.spongepowered.api.text.action.TextActions

object UI {

    object Button {
        private val btnDelete = Text.builder("[*]").red()
        private val btnView = Text.builder("[...]").aqua()

        fun delete(command: String): Text =
            btnDelete.runCommand(command)

        fun delete(command: String, hover: Text): Text =
            btnDelete.onHover(TextActions.showText(hover)).runCommand(command)

        fun view(command: String): Text =
            btnView.runCommand(command)

        fun view(command: String, hover: Text): Text =
            btnView.onHover(TextActions.showText(hover)).runCommand(command)
    }
}