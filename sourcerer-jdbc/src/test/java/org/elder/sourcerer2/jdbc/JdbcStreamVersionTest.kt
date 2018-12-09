package org.elder.sourcerer2.jdbc

import org.elder.sourcerer2.StreamVersion
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class JdbcStreamVersionTest {
    @Test
    fun canFormatJdbcVersion() {
        val timestamp = Instant.parse("2018-12-24T12:01:02.123456789Z")
        val sequence = 425
        val streamVersion = JdbcStreamVersion(timestamp, sequence).toStreamVersion()
        assertEquals("20181224120102.123456789:0425", streamVersion.version)
    }

    @Test
    fun canFormatJdbcVersionWithLeadingZeros() {
        val timestamp = Instant.parse("2018-02-04T02:01:02.012345678Z")
        val sequence = 425
        val streamVersion = JdbcStreamVersion(timestamp, sequence).toStreamVersion()
        assertEquals("20180204020102.012345678:0425", streamVersion.version)
    }

    @Test
    fun canParseJdbcVersion() {
        val streamVersion = StreamVersion.ofString("20180204020102.012345678:0425")
        val jdbcVersion = streamVersion.toJdbcStreamVersion()
        assertEquals(Instant.parse("2018-02-04T02:01:02.012345678Z"), jdbcVersion.timestamp)
        assertEquals(425, jdbcVersion.batchSequenceNr)
    }
}
