package org.tpunn.autoblade.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Generates a service that manages the creation, updation, deletion, and searching of sub-blades. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Repository {
}
