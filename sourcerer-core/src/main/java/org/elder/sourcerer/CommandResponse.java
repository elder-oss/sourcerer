package org.elder.sourcerer;

public class CommandResponse {
    private final String id;
    private final Integer previousVersion;
    private final Integer newVersion;
    private final Boolean noOp;

    public static CommandResponse of(final CommandResult<?> result) {
        return new CommandResponse(
                result.getAggregateId(),
                result.getPreviousVersion(),
                result.getNewVersion(),
                result.getEvents() == null || result.getEvents().isEmpty());
    }

    public CommandResponse() {
        this(null, null, null, null);
    }

    public CommandResponse(
            final String id,
            final Integer previousVersion,
            final Integer newVersion,
            final Boolean noOp) {
        this.id = id;
        this.previousVersion = previousVersion;
        this.newVersion = newVersion;
        this.noOp = noOp;
    }

    /**
     * Gets the id of the aggregate that was operated on.
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the previous version of the aggregate, after the events from the command were applied.
     * This may be null in the cases where the current version is unknown, e.g. a no-op operation.
     * For non atomic operations, this may be deduced from the new version and number of events
     * written, rather than by explicitly reading from the stream before appending changes.
     */
    public Integer getPreviousVersion() {
        return previousVersion;
    }

    /**
     * Gets the new version of the aggregate, after the events from the command were applied.
     * This may be null in the cases where the current version is unknown, e.g. a no-op operation.
     */
    public Integer getNewVersion() {
        return newVersion;
    }

    /**
     * True if the operation was a no-op, i.e. no modification was made.
     */
    public Boolean getNoOp() {
        return noOp;
    }

    @Override
    public String toString() {
        return "CommandResponse{" +
                "id='" + id + '\'' +
                ", previousVersion=" + previousVersion +
                ", newVersion=" + newVersion +
                ", noOp=" + noOp +
                '}';
    }
}
