package org.tpunn.autobladekt

import java.util.UUID
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class AppServiceImpl @Inject constructor() : AppService {
    override fun hello(): String {
        return "Hello from Kotlin App Service!"
    }
}
