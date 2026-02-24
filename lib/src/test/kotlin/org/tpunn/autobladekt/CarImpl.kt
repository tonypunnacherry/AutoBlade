package org.tpunn.autobladekt

import org.tpunn.autoblade.annotations.AutoBuilder;

import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

@AutoBuilder
class CarImpl @AssistedInject constructor(
    @Assisted override val brand: String,
    @Assisted override val mileage: Int
) : Car {
    override fun getSummary(): String {
        return "$brand with $mileage miles"
    }
}
