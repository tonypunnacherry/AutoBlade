package org.tpunn.autoblade;

import static org.junit.Assert.assertEquals;

import java.util.UUID;

import org.junit.Test;

public class BasicTest {
    @Test
    public void test() {
        AppBlade app = AutoBladeApp.start();

        app.teams().create(new TeamData("team1", "Cool Team"));
        TeamBlade team = app.teams().get("team1");
        UUID id = UUID.randomUUID();
        PlayerBlade player = team.players().create(new PlayerData(id, "Tony"));
        ScoreManager score = player.score();
        score.set(10);
        score.add(5);

        assertEquals(15, player.score().get());

        // Deep searching
        app.teams().findPlayer(id).ifPresent(p -> p.score().add(2));
        assertEquals(17, player.score().get());

        // Custom searching
        app.teams().findPlayerInTeam("team1", id).score().add(2);
        assertEquals(19, player.score().get());

        // Test legacy
        assertEquals("https://legacy-api.com", app.legacy().getUrl());

        // Test factory
        PlayerBadge badge = player.badgeFactory().create("MVP");
        assertEquals("MVP for Tony", badge.getName());

        // Test builder
        PlayerBadge badge2 = player.badgeBuilder().name("All-Star").build();
        assertEquals("All-Star for Tony", badge2.getName());
    }
}
