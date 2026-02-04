package org.tpunn.autoblade.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Adds a create method to a blade's repository to generate sub-blades. */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.SOURCE)
public @interface Create {
}
