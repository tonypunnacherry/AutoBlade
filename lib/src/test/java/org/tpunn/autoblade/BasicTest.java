package org.tpunn.autoblade;

import org.junit.Test;

public class BasicTest {
    @Test
    public void test() {
        AppBlade app = DaggerAppBlade_Auto.create();

        app.userRepo().create(
                new UserData("jdoe", "John Doe"));
            
        app.userRepo().get("jdoe").dashboard().display();
    }
}
