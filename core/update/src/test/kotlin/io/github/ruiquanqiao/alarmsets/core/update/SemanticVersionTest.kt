package io.github.ruiquanqiao.alarmsets.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticVersionTest {

    private fun v(raw: String) = SemanticVersion.parseOrNull(raw)!!

    @Test
    fun `strips a leading v`() {
        assertEquals(listOf(1, 4, 2), v("v1.4.2").numbers)
        assertEquals(listOf(1, 4, 2), v("1.4.2").numbers)
    }

    @Test
    fun `orders by numeric component`() {
        assertTrue(v("1.4.2") < v("1.10.0"))
        assertTrue(v("2.0.0") > v("1.99.99"))
        assertTrue(v("1.2.3") < v("1.2.4"))
    }

    @Test
    fun `missing components count as zero`() {
        assertEquals(0, v("1.2").compareTo(v("1.2.0")))
        assertTrue(v("1.2") < v("1.2.1"))
    }

    @Test
    fun `a pre-release is older than the same release`() {
        // The whole point: shipping 1.3.0 must not look like a downgrade to
        // someone running 1.3.0-beta2.
        assertTrue(v("1.3.0-beta2") < v("1.3.0"))
        assertTrue(v("1.3.0") > v("1.3.0-rc1"))
    }

    @Test
    fun `build metadata is ignored`() {
        assertEquals(0, v("1.3.0+build77").compareTo(v("1.3.0")))
    }

    @Test
    fun `rejects things that are not versions`() {
        assertNull(SemanticVersion.parseOrNull("latest"))
        assertNull(SemanticVersion.parseOrNull(""))
        assertNull(SemanticVersion.parseOrNull("v"))
    }
}
