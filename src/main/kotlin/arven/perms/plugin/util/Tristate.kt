package arven.perms.plugin.util

import org.spongepowered.api.util.Tristate

fun Boolean?.toTristate(): Tristate = when (this) {
    true  -> Tristate.TRUE
    false -> Tristate.FALSE
    null  -> Tristate.UNDEFINED
}

fun Tristate.toBoolean(): Boolean? = when (this) {
    Tristate.TRUE      -> true
    Tristate.FALSE     -> false
    Tristate.UNDEFINED -> null
}