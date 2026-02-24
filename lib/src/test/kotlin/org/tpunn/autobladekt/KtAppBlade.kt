package org.tpunn.autobladekt

import org.tpunn.autoblade.annotations.Blade

@Blade
interface KtAppBlade {
    fun appService(): AppService
    fun car(): CarBuilder
}
