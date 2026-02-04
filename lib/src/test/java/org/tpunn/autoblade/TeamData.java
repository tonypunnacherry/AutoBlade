package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Id;
import org.tpunn.autoblade.annotations.Seed;

@Seed("Team")
public record TeamData(
        @Id String teamId,
        String teamName
) {}
