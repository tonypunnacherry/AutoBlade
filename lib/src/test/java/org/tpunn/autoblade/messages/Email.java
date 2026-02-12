package org.tpunn.autoblade.messages;

import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.AutoFactory;
import org.tpunn.autoblade.core.Anchor;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

@MessageStrategy(MessageType.EMAIL)
@AutoFactory(named = "EmailMessageGenerator")
@Anchored(Anchor.PLAYER)
public class Email implements Message {
    private String message;

    @AssistedInject
    public Email(@Assisted String message) {
        this.message = message;
    }

    @Override
    public void send() {
        System.out.println("Sending email... " + message);
    }
}
