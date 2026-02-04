package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Id;
import org.tpunn.autoblade.annotations.Seed;

@Seed("User")
public record UserData(
        @Id String userId,
        String username
) {}
