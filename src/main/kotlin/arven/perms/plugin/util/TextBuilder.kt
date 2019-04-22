package arven.perms.plugin.util

import org.spongepowered.api.text.Text

operator fun Text.Builder.plusAssign(child: Text) {
    this.append(child)
}