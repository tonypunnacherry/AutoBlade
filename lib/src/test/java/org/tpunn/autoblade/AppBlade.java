package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Blade;

@Blade(legacy = {LegacyModule.class})
public interface AppBlade {
    TeamRepository teams();
    LegacyClient legacy();
}