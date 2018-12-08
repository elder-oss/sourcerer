package org.elder.sourcerer2;

public enum ExpectedVersionType {
    /**
     * Any current version (including not currently existing).
     */
    ANY,

    /**
     * Any version, as long as the stream is already created.
     */
    ANY_EXISTING,

    /**
     * Exactly a specified version (version set as an integer in ExpectedVersion).
     */
    EXACTLY,

    /**
     * Not yet created streams only.
     */
    NOT_CREATED
}
