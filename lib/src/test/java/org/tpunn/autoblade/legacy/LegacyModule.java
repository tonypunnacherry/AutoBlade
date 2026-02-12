package org.tpunn.autoblade.legacy;

import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

@Module
public class LegacyModule {
    @Provides
    @Singleton
    public LegacyClient provideLegacyClient() {
        return new LegacyClient("https://legacy-api.com");
    }
}
