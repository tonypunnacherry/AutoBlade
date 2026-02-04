package org.tpunn.autoblade;

import java.util.Optional;

import org.tpunn.autoblade.annotations.Create;
import org.tpunn.autoblade.annotations.Lookup;
import org.tpunn.autoblade.annotations.Repository;

@Repository
public interface TeamRepository {
    @Create
    TeamBlade create(TeamData team);

    @Lookup
    TeamBlade get(String teamId);

    @Lookup
    Optional<PlayerBlade> findPlayer(String playerId);
}