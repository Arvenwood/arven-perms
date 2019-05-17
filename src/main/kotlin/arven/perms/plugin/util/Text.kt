package arven.perms.plugin.util

import org.spongepowered.api.text.Text
import org.spongepowered.api.text.serializer.TextSerializers

operator fun String.unaryPlus(): Text = TextSerializers.FORMATTING_CODE.deserialize(this)