package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.Create;
import org.tpunn.autoblade.annotations.Lookup;
import org.tpunn.autoblade.annotations.Repository;

@Repository
@Anchored("Team")
public interface PlayerRepository {
    @Create
    PlayerBlade create(PlayerData user);

    @Lookup
    PlayerBlade get(String userId);
}