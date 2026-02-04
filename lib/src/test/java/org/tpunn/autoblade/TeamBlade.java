package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Blade;

@Blade(Anchor.TEAM)
public interface TeamBlade {
    PlayerRepository players();
}
