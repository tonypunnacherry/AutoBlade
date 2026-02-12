package org.tpunn.autoblade.actions;

import javax.inject.Inject;

import org.tpunn.autoblade.PlayerData;
import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.Scoped;
import org.tpunn.autoblade.core.Anchor;

@ActionStrategy(ActionType.JUMP)
@Scoped
@Anchored(Anchor.PLAYER)
public class JumpAction implements PlayerAction {
    private final PlayerData data;
    private int jumpCount = 0;

    @Inject
    public JumpAction(PlayerData data) {
         // Use player data if needed
        this.data = data;
    }

    @Override
    public void act() {
        System.out.println("Jumping for " + data.username() + "! Jump count: " + (++jumpCount));
    }
}
