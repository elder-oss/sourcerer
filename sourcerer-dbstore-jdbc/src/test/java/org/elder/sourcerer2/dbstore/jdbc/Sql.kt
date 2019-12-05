package org.elder.sourcerer2.dbstore.jdbc

object Sql {
    val CREATE_DB_SCHEMA = """
        DROP ALL OBJECTS;

        CREATE TABLE events (
            stream_hash INT NOT NULL,
            namespace VARCHAR(32) NOT NULL,
            repository VARCHAR(32) NOT NULL,
            stream_id VARCHAR(128) NOT NULL,
            timestamp TIMESTAMP(6) NOT NULL,
            transaction_seq_nr INT NOT NULL,
            event_id VARCHAR(36) NOT NULL,
            event_type VARCHAR(64) NOT NULL,
            data TEXT NOT NULL,
            metadata TEXT NOT NULL,
            PRIMARY KEY (stream_hash, namespace, repository, stream_id, timestamp, transaction_seq_nr)
        );

        CREATE UNIQUE INDEX repository_idx ON events (
            stream_hash,
            namespace,
            repository,
            timestamp,
            stream_id,
            transaction_seq_nr
        );

        CREATE INDEX timestamp_idx ON events (
            namespace,
            repository,
            timestamp
        );""".trimIndent()
}
