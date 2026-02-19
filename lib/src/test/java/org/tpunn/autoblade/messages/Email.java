package org.tpunn.autoblade.messages;

import org.tpunn.autoblade.PlayerData;
import org.tpunn.autoblade.TeamData;
import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.AutoFactory;
import org.tpunn.autoblade.core.Anchor;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

@MessageStrategy(MessageType.EMAIL)
@AutoFactory(named = "EmailMessageGenerator")
@Anchored(Anchor.PLAYER)
public class Email implements Message {
    private final String message;
    private final PlayerData playerData;
    private final TeamData teamData;

    @AssistedInject
    public Email(@Assisted String message, PlayerData playerData, TeamData teamData) {
        this.message = message;
        this.playerData = playerData;
        this.teamData = teamData;
    }

    @Override
    public void send() {
        System.out.println("Sending email... " + message + " to " + playerData.username() + " of team " + teamData.teamName());
    }
}
