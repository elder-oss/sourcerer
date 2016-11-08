package org.elder.sourcerer;

public class MetadataDecoratorCommandPostProcessor implements CommandPostProcessor {
    private final MetadataDecorator metadataDecorator;

    public MetadataDecoratorCommandPostProcessor(final MetadataDecorator metadataDecorator) {
        this.metadataDecorator = metadataDecorator;
    }

    @Override
    public void postProcessCommand(final Command<?, ?, ?> command) {
        command.addMetadataDecorator(metadataDecorator);
    }
}
