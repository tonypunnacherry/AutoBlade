package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Id;
import org.tpunn.autoblade.annotations.Seed;

@Seed("Player")
public record PlayerData(
        @Id String userId,
        String username
) {}
