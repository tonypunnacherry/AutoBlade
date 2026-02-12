package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Id;
import org.tpunn.autoblade.annotations.Seed;
import org.tpunn.autoblade.core.Anchor;

@Seed(Anchor.TEAM)
public record TeamData(
        @Id String teamId,
        String teamName
) {}
