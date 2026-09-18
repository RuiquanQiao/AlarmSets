package io.github.ruiquanqiao.alarmsets.core.update

/**
 * Just enough of semantic versioning to answer "is the release newer than what
 * is installed".
 *
 * Tolerates a leading `v`, extra numeric components, and a pre-release suffix.
 * A pre-release sorts *below* the same numbers without one, so `1.2.0-beta1`
 * never looks newer than `1.2.0`.
 */
data class SemanticVersion(
    val numbers: List<Int>,
    val preRelease: String?,
) : Comparable<SemanticVersion> {

    override fun compareTo(other: SemanticVersion): Int {
        val size = maxOf(numbers.size, other.numbers.size)
        for (i in 0 until size) {
            val a = numbers.getOrElse(i) { 0 }
            val b = other.numbers.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return when {
            preRelease == null && other.preRelease == null -> 0
            preRelease == null -> 1
            other.preRelease == null -> -1
            else -> preRelease.compareTo(other.preRelease)
        }
    }

    override fun toString(): String =
        numbers.joinToString(".") + (preRelease?.let { "-$it" } ?: "")

    companion object {
        /** Returns null for anything that does not start with a number. */
        fun parseOrNull(raw: String): SemanticVersion? {
            val trimmed = raw.trim().removePrefix("v").removePrefix("V")
            if (trimmed.isEmpty()) return null

            val plusStripped = trimmed.substringBefore('+')
            val core = plusStripped.substringBefore('-')
            val pre = plusStripped.substringAfter('-', "").ifBlank { null }

            val numbers = core.split('.').map { part ->
                part.takeWhile { it.isDigit() }.toIntOrNull() ?: return null
            }
            if (numbers.isEmpty()) return null
            return SemanticVersion(numbers, pre)
        }
    }
}
