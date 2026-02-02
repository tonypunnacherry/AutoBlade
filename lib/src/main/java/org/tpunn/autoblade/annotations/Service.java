package org.tpunn.autoblade.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** A stateless worker. Becomes transient in whatever scope it is placed in. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Service {}