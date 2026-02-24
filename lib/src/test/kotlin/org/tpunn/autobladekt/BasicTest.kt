package org.tpunn.autobladekt

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class BasicKotlinTest {
    @Test
    fun `test`() {
        val app = AutoBladeApp.start();
        app.appService().hello().let { result ->
            assertEquals("Hello from Kotlin App Service!", result)
        }
        app.car().brand("test").mileage(300).build().getSummary().let { result ->
            assertEquals("test with 300 miles", result)
        }
    }
}
