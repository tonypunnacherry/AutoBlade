package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Blade;

@Blade("Team")
public interface TeamBlade {
    PlayerRepository players();
}
