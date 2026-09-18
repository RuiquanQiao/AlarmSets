package io.github.ruiquanqiao.alarmsets.core.update

/**
 * Turns a GitHub release body into something readable in a plain text view.
 *
 * Release notes are Markdown. Dropping them straight into a `Text` shows the
 * reader literal `**bold**` and `### Heading`, which looks broken. Rendering
 * real Markdown would mean pulling in a rendering library for one small card,
 * so this flattens the handful of constructs that actually appear in release
 * notes instead.
 */
object ReleaseNotes {

    private val FENCED_CODE = Regex("```[\\s\\S]*?```")
    private val HEADING = Regex("^\\s{0,3}#{1,6}\\s*", RegexOption.MULTILINE)
    private val BOLD_ITALIC = Regex("(\\*{1,3}|_{1,3})(?=\\S)(.*?\\S)\\1")
    private val INLINE_CODE = Regex("`([^`]*)`")
    private val LINK = Regex("\\[([^\\]]+)]\\(([^)]+)\\)")
    private val IMAGE = Regex("!\\[[^\\]]*]\\([^)]+\\)")
    private val BULLET = Regex("^\\s{0,3}[-*+]\\s+", RegexOption.MULTILINE)
    private val BLOCKQUOTE = Regex("^\\s{0,3}>\\s?", RegexOption.MULTILINE)
    private val HORIZONTAL_RULE = Regex("^\\s{0,3}([-*_])\\s*(\\1\\s*){2,}$", RegexOption.MULTILINE)
    private val EXTRA_BLANK_LINES = Regex("\n{3,}")

    /**
     * @param maxLines keeps the card from swallowing the screen when a release
     *        has long notes. The full text is always on the release page.
     */
    fun toPlainText(markdown: String, maxLines: Int = 12): String {
        if (markdown.isBlank()) return ""

        var text = markdown
        text = IMAGE.replace(text, "")
        text = FENCED_CODE.replace(text, "")
        text = HORIZONTAL_RULE.replace(text, "")
        text = HEADING.replace(text, "")
        text = BLOCKQUOTE.replace(text, "")
        text = LINK.replace(text) { it.groupValues[1] }
        text = INLINE_CODE.replace(text) { it.groupValues[1] }
        text = BOLD_ITALIC.replace(text) { it.groupValues[2] }
        text = BULLET.replace(text, "• ")
        text = EXTRA_BLANK_LINES.replace(text, "\n\n")

        val lines = text.trim().lines()
        val kept = lines.take(maxLines)
        val truncated = lines.size > maxLines

        return kept.joinToString("\n").trimEnd() + if (truncated) "\n…" else ""
    }
}
