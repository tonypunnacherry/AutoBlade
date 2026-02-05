package org.tpunn.autoblade.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Blade {
    /** Optional: Specify a custom name for the generated subcomponent. */
    String value() default "App";
    /** Optional: Specify a list of legacy Dagger modules that should be included in the generated subcomponent. */
    Class<?>[] legacy() default {};
}