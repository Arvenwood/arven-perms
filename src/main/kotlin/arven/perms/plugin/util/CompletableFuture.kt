package arven.perms.plugin.util

import java.util.concurrent.CompletableFuture

inline val <T> T.future: CompletableFuture<T>
    get() = CompletableFuture.completedFuture(this)