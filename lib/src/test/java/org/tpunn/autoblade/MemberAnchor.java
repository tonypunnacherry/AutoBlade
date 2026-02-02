package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Anchor;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Anchor("User")
@Retention(RetentionPolicy.RUNTIME)
public @interface MemberAnchor {
}