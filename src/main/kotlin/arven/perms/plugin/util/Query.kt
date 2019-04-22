package arven.perms.plugin.util

import org.jetbrains.exposed.sql.Query

inline val Query.isNotEmpty: Boolean
    get() = !this.empty()