package arven.perms.plugin.util

/**
 * Given a permission 'foo.bar.apple.banana', returns a list:
 * - 'foo.bar.apple.banana'
 * - 'foo.bar.apple'
 * - 'foo.bar'
 * - 'foo'
 */
fun String.toTreeList(): List<String> {
    var current = this
    val result = arrayListOf<String>()

    result += current

    while ('.' in current) {
        current = current.substringBeforeLast('.')
        result += current
    }

    return result
}