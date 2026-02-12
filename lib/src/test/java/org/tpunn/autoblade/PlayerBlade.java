package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Blade;

@Blade(Anchor.PLAYER)
public interface PlayerBlade {
    ScoreManager score();
    PlayerBadgeFactory badgeFactory();
    PlayerBadgeBuilder badgeBuilder();
    ActionStrategyResolver actions();
    MessageStrategyResolver messages();
}