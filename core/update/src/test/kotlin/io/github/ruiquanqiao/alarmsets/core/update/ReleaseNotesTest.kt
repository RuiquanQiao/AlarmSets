package io.github.ruiquanqiao.alarmsets.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    @Test
    fun `strips the markdown that showed up raw on screen`() {
        val source = """
            First public build. An alarm clock whose unit is a **set**.

            ### The rule this app exists for

            A set's switch is a *mask*, not a bulk edit.
        """.trimIndent()

        val plain = ReleaseNotes.toPlainText(source)

        assertFalse(plain.contains("**"))
        assertFalse(plain.contains("###"))
        assertTrue(plain.contains("a set."))
        assertTrue(plain.contains("The rule this app exists for"))
        assertTrue(plain.contains("a mask, not a bulk edit"))
    }

    @Test
    fun `keeps link text and drops the url`() {
        val plain = ReleaseNotes.toPlainText("Use [Obtainium](https://example.com/x) to update.")
        assertEquals("Use Obtainium to update.", plain)
    }

    @Test
    fun `turns list markers into bullets`() {
        val plain = ReleaseNotes.toPlainText("- one\n- two\n* three")
        assertEquals("• one\n• two\n• three", plain)
    }

    @Test
    fun `removes fenced code blocks and images`() {
        val plain = ReleaseNotes.toPlainText(
            "Checksum below\n\n```\nabc123\n```\n\n![shot](https://example.com/a.png)\ndone",
        )
        assertFalse(plain.contains("abc123"))
        assertFalse(plain.contains("example.com"))
        assertTrue(plain.contains("Checksum below"))
        assertTrue(plain.contains("done"))
    }

    @Test
    fun `unwraps inline code`() {
        assertEquals("run gradlew test", ReleaseNotes.toPlainText("run `gradlew test`"))
    }

    @Test
    fun `truncates long notes and marks that it did`() {
        val long = (1..40).joinToString("\n") { "line $it" }
        val plain = ReleaseNotes.toPlainText(long, maxLines = 5)
        assertEquals(6, plain.lines().size) // 5 kept plus the ellipsis line
        assertTrue(plain.endsWith("…"))
        assertTrue(plain.startsWith("line 1"))
    }

    @Test
    fun `blank input stays blank`() {
        assertEquals("", ReleaseNotes.toPlainText("   \n\n "))
    }
}
