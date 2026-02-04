package org.tpunn.autoblade;

import org.junit.Test;

public class BasicTest {
    @Test
    public void test() {
        AppBlade appBlade = DaggerAppBlade_Auto.create();

        appBlade.getUserRepository().create(
                new User("jdoe", "John Doe"));
            
        appBlade.getUserRepository().get("jdoe").getUserDashboard().display();
    }
}
