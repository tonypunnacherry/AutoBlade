package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Blade;
import org.tpunn.autoblade.core.Anchor;
import org.tpunn.autoblade.repos.PlayerRepository;

@Blade(Anchor.TEAM)
public interface TeamBlade {
    PlayerRepository players();
}
