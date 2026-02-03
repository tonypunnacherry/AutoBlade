package org.tpunn.autoblade;

import org.junit.Test;

public class BasicTest {
    @Test
    public void test() {
        AppBlade appBlade = DaggerAppBlade.create();

        UserBlade userBlade = appBlade.getUserBuilder()
            .seed(new User("jdoe", "John Doe"))
            .build();
            
        userBlade.getUserDashboard().display();
    }
}
