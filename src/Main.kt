//Nov 24th, 2024
//Stock Analysis
//by Luke Murphy

import java.nio.file.Path
import java.nio.file.Paths
import OHLCDate
import java.io.File
import java.lang.Math.pow
import kotlin.math.pow


//Constants
//val path_nasdaq: String = System.getProperty("user.home")+"/Documents/stock market/stock_market_data/nasdaq/csv"
//val path_forbes2000: String = System.getProperty("user.home")+"/Documents/stock market/stock_market_data/forbes2000/csv"
//val path_nyse:String = System.getProperty("user.home")+"/Documents/stock market/stock_market_data/nyse/csv"
val path_sp500: String = System.getProperty("user.home")+"/Documents/stock market/stock_market_data/sp500/csv"
val path_base: String = System.getProperty("user.home")+"/Documents/stock market/stock_market_data"
val path_small: String = System.getProperty("user.home")+"/Documents/stock market/stock_market_data_small"

fun main() {
    //Create market
    val stockMarket = MarketList()

    stockMarket.scrape_add(path_small)

    //try support 0.3
    stockMarket.Apriori(minsup = 0.10, minconf = 0.50)

    //MAIN TEST: minsup = 0.40, minconf = 0.90
}


