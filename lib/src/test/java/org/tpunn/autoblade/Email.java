package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Anchored;
import org.tpunn.autoblade.annotations.AutoBuilder;

import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

@MessageStrategy(MessageType.EMAIL)
@AutoBuilder
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
