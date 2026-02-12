package org.tpunn.autoblade.actions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.tpunn.autoblade.annotations.Strategy;

/**
 * Choose a strategy for taking "player actions"
 */
@Strategy
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface ActionStrategy {
    ActionType value();
}
