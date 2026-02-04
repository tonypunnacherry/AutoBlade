package org.tpunn.autoblade;

import java.util.Optional;
import java.util.UUID;

import org.tpunn.autoblade.annotations.Concurrent;
import org.tpunn.autoblade.annotations.Create;
import org.tpunn.autoblade.annotations.Lookup;
import org.tpunn.autoblade.annotations.Repository;

@Repository
@Concurrent
public abstract class ExtendedTeamRepository implements TeamRepository {
    @Create
    public abstract TeamBlade create(TeamData team);

    @Lookup
    public abstract TeamBlade get(String teamId);

    @Lookup
    public abstract Optional<PlayerBlade> findPlayer(UUID playerId);

    // Custom implementation method
    @Override
    public PlayerBlade findPlayerInTeam(String teamId, UUID playerId) {
        return get(teamId).players().get(playerId);
    }
}