package arven.perms.plugin.util

import frontier.ske.text.aqua
import frontier.ske.text.red
import frontier.ske.text.runCommand
import frontier.ske.text.showText
import org.spongepowered.api.text.Text

object UI {

    object Button {
        private val btnDelete = Text.builder("[*]").red()
        private val btnView = Text.builder("[...]").aqua()

        fun delete(command: String): Text =
            btnDelete.runCommand(command).build()

        fun delete(command: String, hover: Text): Text =
            btnDelete.runCommand(command).showText(hover).build()

        fun view(command: String): Text =
            btnView.runCommand(command).build()

        fun view(command: String, hover: Text): Text =
            btnView.runCommand(command).showText(hover).build()
    }
}