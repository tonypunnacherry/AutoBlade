package org.tpunn.autoblade;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BasicTest {
    @Test
    public void test() {
        AppBlade app = DaggerAppBlade_Auto.create();

        app.teams().create(new TeamData("team1", "Cool Team"));
        TeamBlade team = app.teams().get("team1");
        PlayerBlade player = team.players().create(new PlayerData("player1", "Tony"));
        ScoreManager score = player.score();
        score.set(10);
        score.add(5);

        assertEquals(15, player.score().get());

        // Deep searching
        app.teams().findPlayer("player1").ifPresent(p -> p.score().add(2));

        assertEquals(17, player.score().get());
    }
}
