package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Blade;

@Blade("User")
public interface UserBlade {
    UserDashboard dashboard();
}