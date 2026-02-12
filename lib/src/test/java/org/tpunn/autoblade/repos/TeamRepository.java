package org.tpunn.autoblade.repos;

import java.util.Optional;
import java.util.UUID;

import org.tpunn.autoblade.PlayerBlade;
import org.tpunn.autoblade.TeamBlade;
import org.tpunn.autoblade.TeamData;

public interface TeamRepository {
    TeamBlade create(TeamData team);
    TeamBlade get(String teamId);
    Optional<PlayerBlade> findPlayer(UUID playerId);
    PlayerBlade findPlayerInTeam(String teamId, UUID playerId);
}