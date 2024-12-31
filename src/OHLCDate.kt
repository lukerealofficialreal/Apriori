
/*
The OHLC_Date class is a structure which holds the Open, High, Low, and Close data
for a particular stock on a particular date.
This implementation also stores the volume and adjusted close
*/
data class OHLCDate (
    var market: String = "",
    var ticker: String = "",

    var open: Float = 0.0f,
    //var high: Float = Float.MAX_VALUE,
    //var low: Float = 0.0f,
    var close: Float = 0.0f,
    var volume: Int = 0,
    //var adjClose: Float = 0.0f,
    ) {
    fun altToString(): String {
        return ticker
    }
}
