package org.tpunn.autoblade;

import java.util.UUID;

import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.Create;
import org.tpunn.autoblade.annotations.Lookup;
import org.tpunn.autoblade.annotations.Repository;

@Repository
@Anchored(Anchor.TEAM)
public interface PlayerRepository {
    @Create
    PlayerBlade create(PlayerData user);

    @Lookup
    PlayerBlade get(UUID userId);
}