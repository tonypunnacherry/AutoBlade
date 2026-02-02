package org.tpunn.autoblade;

import org.junit.Test;

public class BasicTest {
    @Test
    public void test() {
        AppComponent component = DaggerAppComponent.create();

        UserComponent user1 = component.getUserBuilder().seed(new User("001", "tpunn")).build();

        user1.getUserDashboard().display();
    }
}
