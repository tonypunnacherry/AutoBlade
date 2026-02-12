package org.tpunn.autoblade;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.UUID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BasicTest {
    private String lastLine;
    private final PrintStream originalOut = System.out;

    @Before
    public void setUp() {
        System.setOut(new PrintStream(new ByteArrayOutputStream()) {
            @Override
            public void println(String s) {
                lastLine = s;
                originalOut.println(s); // Optional: still print to real console
            }
        });
    }

    @After
    public void tearDown() {
        System.setOut(originalOut);
    }

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

        // Test strategy
        player.actions().resolve(ActionType.JUMP).act();
        assertEquals("Jumping for Tony! Jump count: 1", lastLine);
        player.actions().resolve(ActionType.JUMP).act();
        assertEquals("Jumping for Tony! Jump count: 2", lastLine);
        player.actions().resolve(ActionType.JUMP).act();
        assertEquals("Jumping for Tony! Jump count: 3", lastLine);

        player.actions().resolve(ActionType.SIT).act();
        assertEquals("Sitting for Tony! Sitting: true", lastLine);
        player.actions().resolve(ActionType.SIT).act();
        assertEquals("Sitting for Tony! Sitting: false", lastLine);

        player.messages().resolve(MessageType.EMAIL).create("Hello Tony!").send();
        assertEquals("Sending email... Hello Tony!", lastLine);
    }
}
