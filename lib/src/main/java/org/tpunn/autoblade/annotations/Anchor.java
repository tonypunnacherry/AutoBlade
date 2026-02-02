package org.tpunn.autoblade.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Anchor {
    /**
     * Optional prefix override.
     * If empty, the prefix is inferred from the annotation name.
     */
    String value() default "";
}