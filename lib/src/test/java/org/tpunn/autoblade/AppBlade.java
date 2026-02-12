package org.tpunn.autoblade;

import org.tpunn.autoblade.annotations.Blade;
import org.tpunn.autoblade.legacy.LegacyClient;
import org.tpunn.autoblade.legacy.LegacyModule;
import org.tpunn.autoblade.repos.TeamRepository;

@Blade(legacy = {LegacyModule.class})
public interface AppBlade {
    TeamRepository teams();
    LegacyClient legacy();
}