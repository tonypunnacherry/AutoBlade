package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Id;
import org.tpunn.autoblade.annotations.Seed;

@Seed @MemberAnchor
public record User(
        @Id String userId,
        String username
) {}
