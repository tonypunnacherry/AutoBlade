package org.tpunn.autoblade.actions;

import javax.inject.Inject;

import org.tpunn.autoblade.PlayerData;
import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.Scoped;
import org.tpunn.autoblade.core.Anchor;

@ActionStrategy(ActionType.SIT)
@Scoped
@Anchored(Anchor.PLAYER)
public class SitAction implements PlayerAction {
    private final PlayerData data;
    private boolean sitting = false;

    @Inject
    public SitAction(PlayerData data) {
         // Use player data if needed
        this.data = data;
    }

    @Override
    public void act() {
        sitting = !sitting;
        System.out.println("Sitting for " + data.username() + "! Sitting: " + sitting);
    }
}
