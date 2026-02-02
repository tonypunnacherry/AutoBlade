package org.tpunn.autoblade.annotations;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Identifies the data object that "seeds" a dynamic scope. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Seed {
    Class<? extends Annotation> scope();
}