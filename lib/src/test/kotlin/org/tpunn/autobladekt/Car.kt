package org.tpunn.autobladekt

public interface Car {
    val brand: String
    val mileage: Int
    fun getSummary(): String
}