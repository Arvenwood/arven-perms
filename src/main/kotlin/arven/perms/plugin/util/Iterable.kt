package arven.perms.plugin.util

import java.util.*

inline fun <T> T.collectAll(producer: (T) -> Iterable<T>): ArrayList<T> {
    val result = arrayListOf(this)

    val queue = ArrayDeque<T>()
    queue.addAll(producer(this))

    while (queue.isNotEmpty()) {
        val value = queue.removeFirst()
        result.add(value)

        for (produced in producer(value)) {
            queue.addLast(produced)
        }
    }

    return result
}