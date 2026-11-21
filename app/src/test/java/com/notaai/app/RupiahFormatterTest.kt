package com.notaai.app

import com.notaai.app.domain.RupiahFormatter
import org.junit.Assert.assertEquals
import org.junit.Test

class RupiahFormatterTest {
    @Test fun `format rupiah Indonesia`() {
        assertEquals("Rp 0", RupiahFormatter.format(0L))
        assertEquals("Rp 75.000", RupiahFormatter.format(75_000L))
        assertEquals("-Rp 5.000", RupiahFormatter.format(-5_000L))
        assertEquals("+Rp 5.000", RupiahFormatter.formatSignedDifference(5_000L))
    }
}
