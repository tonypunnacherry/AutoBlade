package org.tpunn.autoblade;

import org.tpunn.autoblade.actions.ActionStrategyResolver;
import org.tpunn.autoblade.messages.MessageStrategyResolver;
import org.tpunn.autoblade.annotations.Blade;
import org.tpunn.autoblade.badges.PlayerBadgeBuilder;
import org.tpunn.autoblade.badges.PlayerBadgeMachine;
import org.tpunn.autoblade.core.Anchor;
import org.tpunn.autoblade.scores.ScoreManager;

@Blade(Anchor.PLAYER)
public interface PlayerBlade {
    ScoreManager score();
    PlayerBadgeMachine badgeFactory();
    PlayerBadgeBuilder badgeBuilder();
    ActionStrategyResolver actions();
    MessageStrategyResolver messages();
}