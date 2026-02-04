package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Blade;

@Blade
public interface AppBlade {
    UserRepository getUserRepository();
}