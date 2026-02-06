package org.tpunn.autoblade;

import javax.inject.Inject;

import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.Scoped;

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
