package org.tpunn.autoblade;

import java.util.Optional;
import java.util.UUID;

public interface TeamRepository {
    TeamBlade create(TeamData team);
    TeamBlade get(String teamId);
    Optional<PlayerBlade> findPlayer(UUID playerId);
    PlayerBlade findPlayerInTeam(String teamId, UUID playerId);
}