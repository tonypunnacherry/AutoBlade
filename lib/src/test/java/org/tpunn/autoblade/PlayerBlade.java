package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Blade;

@Blade("Player")
public interface PlayerBlade {
    ScoreManager score();
}