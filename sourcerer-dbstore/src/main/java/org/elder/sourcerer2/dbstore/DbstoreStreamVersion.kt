package org.elder.sourcerer2.dbstore

import org.elder.sourcerer2.StreamVersion
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder

data class DbstoreStreamVersion(
        val timestamp: Instant,
        val transactionSequenceNr: Int
) {
    companion object {
        val stringFormat = Regex("""\d{14}\.\d{9}:\d{4}""")
        val dateFormat: DateTimeFormatter = DateTimeFormatterBuilder()
                .appendPattern("yyyyMMddHHmmss.nnnnnnnnn")
                .toFormatter()
    }
}

fun StreamVersion.toDbstoreStreamVersion(): DbstoreStreamVersion {
    // TODO: This format is very verbose and could be easily converted to a shorter one by using
    // a binary representation and a hex/base32/base64 encoding. Keep it human readable to start as
    // it will make it meaningful for a human at a glance.
    if (!DbstoreStreamVersion.stringFormat.matches(this.version)) {
        throw IllegalArgumentException("Version is not in expected format")
    }

    val timestampStr = version.substring(0, 24)
    val timestamp = LocalDateTime
            .parse(timestampStr, DbstoreStreamVersion.dateFormat)
            .toInstant(ZoneOffset.UTC)
    val sequence = Integer.parseInt(version.substring(25, 29))
    return DbstoreStreamVersion(timestamp, sequence)
}

fun DbstoreStreamVersion.toStreamVersion(): StreamVersion {
    if (transactionSequenceNr > 9999 || transactionSequenceNr < 0) {
        throw IllegalArgumentException("Cannot commit more than 10,000 events in one batch")
    }

    val utcLocalDateTime = LocalDateTime.ofInstant(timestamp, ZoneOffset.UTC)
    return StreamVersion.ofString(
            "${DbstoreStreamVersion.dateFormat.format(utcLocalDateTime)}:" +
                    transactionSequenceNr.toString().padStart(4, '0'))
}
