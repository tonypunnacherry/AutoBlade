package org.tpunn.autoblade.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Forces the generated ManagerService to use thread-safe caching. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Concurrent {}