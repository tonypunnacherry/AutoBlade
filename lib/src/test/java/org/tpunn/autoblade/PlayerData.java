package org.tpunn.autoblade;

import java.util.UUID;

import org.tpunn.autoblade.annotations.Id;
import org.tpunn.autoblade.annotations.Seed;
import org.tpunn.autoblade.core.Anchor;

@Seed(Anchor.PLAYER)
public record PlayerData(
        @Id UUID userId,
        String username
) {}
