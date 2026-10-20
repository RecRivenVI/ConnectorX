package io.github.recrivenvi.connectorx.session

/** Positive scrolls up, negative down. Both directions use the fixed wheel rate. */
internal object LocalScrollSpeed {
    const val UNITS_PER_SECOND=io.github.recrivenvi.connectorx.runtime.WheelTimeline.DEFAULT_UNITS_PER_SECOND

    fun units(direction:Int):Float {
        require(direction in -1..1)
        return if(direction==0)0f else UNITS_PER_SECOND
    }
}
