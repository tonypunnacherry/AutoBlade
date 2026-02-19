package org.tpunn.autoblade.repos;

import java.util.UUID;

import org.tpunn.autoblade.PlayerBlade;
import org.tpunn.autoblade.PlayerData;
import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.Create;
import org.tpunn.autoblade.annotations.Lookup;
import org.tpunn.autoblade.annotations.Repository;
import org.tpunn.autoblade.annotations.Source;
import org.tpunn.autoblade.core.Anchor;

@Repository
@Anchored(Anchor.TEAM)
@Source(Anchor.PLAYER)
public interface PlayerRepository {
    @Create
    PlayerBlade create(PlayerData user);

    @Lookup
    PlayerBlade get(UUID userId);
}