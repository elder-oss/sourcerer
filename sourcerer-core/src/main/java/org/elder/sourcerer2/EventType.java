package org.elder.sourcerer2;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used on event types to provide additional configuration about how they are to be
 * persisted.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EventType {
    class NoNormalizer implements EventNormalizer<Object> {
        @Override
        public Object normalizeEvent(final Object event) {
            return event;
        }
    }

    /**
     * The name to use for this type in an event repository.
     */
    String repositoryName() default "";

    /**
     * The event normalizer (optional) to use to post-process events after deserialization.
     */
    Class<? extends EventNormalizer<?>> normalizer() default NoNormalizer.class;
}
