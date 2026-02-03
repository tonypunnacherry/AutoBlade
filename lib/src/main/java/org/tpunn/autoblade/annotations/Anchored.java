package org.tpunn.autoblade.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Anchors a service to a specific seeded subcomponent. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Anchored {
    String value();
}