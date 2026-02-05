package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.AutoBuilder;
import org.tpunn.autoblade.annotations.AutoFactory;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

@AutoBuilder
@AutoFactory(suffix = "Machine")
@Anchored(Anchor.PLAYER)
public class PlayerBadgeImpl implements PlayerBadge {
    private String name;
    private final PlayerData playerData;

    @AssistedInject
    public PlayerBadgeImpl(@Assisted String name, PlayerData playerData) {
        this.name = name;
        this.playerData = playerData;
    }

    @Override
    public String getName() {
        return name + " for " + playerData.username();
    }
}
