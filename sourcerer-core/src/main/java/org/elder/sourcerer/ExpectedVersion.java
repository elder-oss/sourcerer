package org.elder.sourcerer;

import org.elder.sourcerer.exceptions.ConflictingExpectedVersionsException;

/**
 * Describes the version that an aggregate is expected to be in when read, or appended to.
 */
public final class ExpectedVersion {
    private final ExpectedVersionType type;
    private final int expectedVersion;

    private ExpectedVersion(final ExpectedVersionType type, final int expectedVersion) {
        this.type = type;
        this.expectedVersion = expectedVersion;
    }

    /**
     * Gets the type of expected version, see {@link ExpectedVersionType}.
     */
    public ExpectedVersionType getType() {
        return type;
    }

    /**
     * For type == EXACTLY, returns the version that the event stream or aggregate is expected to be
     * in when a command or append is attempted.
     */
    public int getExpectedVersion() {
        return expectedVersion;
    }

    public static ExpectedVersion any() {
        return new ExpectedVersion(ExpectedVersionType.ANY, -1);
    }

    public static ExpectedVersion anyExisting() {
        return new ExpectedVersion(ExpectedVersionType.ANY_EXISTING, -1);
    }

    public static ExpectedVersion notCreated() {
        return new ExpectedVersion(
                ExpectedVersionType.NOT_CREATED,
                AggregateState.VERSION_NOT_CREATED);
    }

    public static ExpectedVersion exactly(final int version) {
        return new ExpectedVersion(ExpectedVersionType.EXACTLY, version);
    }

    public static ExpectedVersion merge(
            final ExpectedVersion baseVersion,
            final ExpectedVersion newVersion) {
        if (baseVersion != null && newVersion != null) {
            switch (baseVersion.getType()) {
                case ANY:
                    // Any is always compatible with other expected version
                    return newVersion;
                case ANY_EXISTING:
                    switch (newVersion.getType()) {
                        case ANY:
                        case ANY_EXISTING:
                            return ExpectedVersion.anyExisting();
                        case EXACTLY:
                            return newVersion;
                        case NOT_CREATED:
                            throw new ConflictingExpectedVersionsException(
                                    "Cannot merge 'any existing' with 'not created'",
                                    baseVersion, newVersion);
                        default:
                            throw new IllegalArgumentException(
                                    "Unrecognized version type: " + newVersion.getType());
                    }
                case EXACTLY:
                    switch (newVersion.getType()) {
                        case ANY:
                        case ANY_EXISTING:
                        case EXACTLY:
                            if (baseVersion.getExpectedVersion()
                                    != newVersion.getExpectedVersion()) {
                                throw new ConflictingExpectedVersionsException(
                                        "Cannot merge 'exactly "
                                                + baseVersion.getExpectedVersion()
                                                + "' with 'exactly "
                                                + newVersion.getExpectedVersion()
                                                + "'",
                                        baseVersion, newVersion);
                            }
                            return baseVersion;
                        case NOT_CREATED:
                            throw new ConflictingExpectedVersionsException(
                                    "Cannot merge 'exactly "
                                            + baseVersion.getExpectedVersion()
                                            + "' with 'not created'",
                                    baseVersion, newVersion);
                        default:
                            throw new IllegalArgumentException(
                                    "Unrecognized version type: " + newVersion.getType());
                    }
                case NOT_CREATED:
                    switch (newVersion.getType()) {
                        case ANY:
                        case NOT_CREATED:
                            break;
                        case ANY_EXISTING:
                            throw new ConflictingExpectedVersionsException(
                                    "Cannot merge 'not created' with 'any existing'",
                                    baseVersion, newVersion);
                        case EXACTLY:
                            throw new ConflictingExpectedVersionsException(
                                    "Cannot merge 'not created' with 'exactly "
                                            + newVersion.getExpectedVersion() + "'",
                                    baseVersion, newVersion);
                        default:
                            break;
                    }
                    return baseVersion;
                default:
                    break; // Throws, see below
            }
            throw new RuntimeException(
                    "Internal error, unexpected version combination merging "
                            + baseVersion
                            + " with " + newVersion);
        } else if (baseVersion != null) {
            return baseVersion;
        } else if (newVersion != null) {
            return newVersion;
        } else {
            return ExpectedVersion.any();
        }
    }

    @Override
    public String toString() {
        return "ExpectedVersion{" +
                "type=" + type +
                ", expectedVersion=" + expectedVersion +
                '}';
    }
}
