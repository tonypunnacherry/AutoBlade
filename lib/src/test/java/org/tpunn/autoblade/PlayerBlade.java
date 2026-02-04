package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Blade;

@Blade(Anchor.PLAYER)
public interface PlayerBlade {
    ScoreManager score();
}