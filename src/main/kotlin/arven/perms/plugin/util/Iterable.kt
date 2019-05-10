package arven.perms.plugin.util

import java.util.*

inline fun <T> T.collectAll(producer: (T) -> Iterable<T>): MutableList<T> {
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

class CycleDetectedException(message: String = "A cycle was detected.") : Exception(message)

/**
 * This method is different from the one above because it detects cycles.
 */
inline fun <T> T.collectAllGuarded(producer: (T) -> Iterable<T>): MutableList<T> {
    val result = hashSetOf(this)

    val queue = ArrayDeque<T>()
    queue.addAll(producer(this))

    while (queue.isNotEmpty()) {
        val value = queue.removeFirst()

        if (!result.add(value)) {
            throw CycleDetectedException("Adding $value would cause a cycle in inheritance.")
        }

        for (produced in producer(value)) {
            if (produced in result) {
                throw CycleDetectedException("Adding $value would cause a cycle in inheritance.")
            }

            queue.addLast(value)
        }
    }

    return result.toMutableList()
}