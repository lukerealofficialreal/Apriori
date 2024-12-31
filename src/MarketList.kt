
import java.io.File
import java.nio.file.FileSystemLoopException
import java.util.*
import kotlin.collections.HashMap


/*
The MarketList class serves as a list for the purposes of storing OHLC_Date objects.
For the purposes of this assignment, new OHLC_Date objects are added to the end of the list.

This implementation also contains a chainList, which is a list of headers to all unique OHLC_Date object 'chains.'
Each OHLC_Date object is a part of a 'chain,' which is a linked list connecting OHLC_Date objects which share similar
properties for the purpose of iterating through them

The MarketList class contains several methods for sorting this data
*/

//Constants

const val HEADER: String = "Date,Low,Open,Volume,High,Close,Adjusted Close" //The header for each ticker file
const val NUM_COLS: Int = 7 //The number of columns in the input file
const val DELIMITER: Char = ',' //The delimiter used to separate data in the file
const val DATE_STR_SIZE: Int = 10 //The number of characters inside a date string of the format DDXMMXYYYY
const val EXP_FACTOR: Int = 2 //The size to multiple list/chainList by when expanded
const val ASSOC_OUT = "Assoc.txt"
const val RULES_OUT = "Rules.txt"

//Global
var file = File(ASSOC_OUT)

//Each Node has an association and references to all associations to which it belongs
class Node(var assoc: Association, var loc: Pair<Int,Int> = Pair(0,0)) {
    val currLayer: Int //the current layer, but also the size of the current layer
        get() = loc.first
}

class MarketList()
{
    //Create the underlying data structure
    var marketMap: HashMap<Int, MutableSet<OHLCDate>> = hashMapOf()
    var marketSets: ArrayList<MutableSet<String>> = arrayListOf()

    //Initialize with one layer to start
    var aprioriArrays: ArrayList<ArrayList<Node>> = arrayListOf(ArrayList()) //The tree which will contain all frequent sets in the database after running Apriori
    var currLevel = 0

    val atomics: ArrayList<Pair<String,Int>> = ArrayList() //an ArrayList which contains all the atomics

    //Precond: The market data has been read
    //Assumption: The number of keys in marketMap = the number of total transactions
    fun Apriori(minsup: Double, minconf: Double) {
        //Step 0.
        //Reset the tree from last run
        //Set the total transactions
        //Prepare marketSets
        //Erase old output assoc.
        prepareMarketSets()
        aprioriArrays = arrayListOf(ArrayList()) //Thank you garbage collector. Very cool,
        Association.num = get_keys().size
        file.writeText("")

        //Step 1.
        //Create the first layer of the tree by adding all atomics which meet the minsup threshold
        //to the tree
        var rootIndex = 0
        for (atomPair in atomics) {
            if (atomPair.second >= minsup) {
                //Minimum confidence is irrelevant for this step because these are the base transactions
                aprioriArrays[0].add(Node(Association(mutableSetOf(atomPair.first), atomPair.second), Pair(0, rootIndex)))
            }
            rootIndex++
        }

        //Step 2.
        //Use the current layer of the tree to create new layers using combinations of the previous layers.
        //New layers must meet the minimum support and the minimum confidence.
        //Repeat until there are no supported transactions remaining


        fun GenFreqItemSets(currNode: Node, opNode: Node): MutableSet<String>? {
            //Generate an item set by combining two previous sets
            //merge two k-1 item sets if their first k-2 items are identical
            val currLayer = currNode.currLayer

            //sets are sorted. To check if the first k-2 items are identical,
            //take the first k-2 items from each and compare
            var currComp: List<String> = listOf()
            var opComp: List<String> = listOf()

            if(currLayer > 0) {
                currComp = currNode.assoc.items.toSortedSet().take(currLayer) //The first k-2 items of current set
                opComp = opNode.assoc.items.toSortedSet().take(currLayer)
            }

            //compare the subsets. if they are equal, a new set can be generated and returned
            if (currComp.equals(opComp)) {
                val result = currNode.assoc.items.toSortedSet()
                result.add(opNode.assoc.items.last())
                return result //Implicit cast from SortedSet to MutableSet
            } else return null
        }

        //function to prune an item set using all the sets of the previous layer
        fun PruneItemSet(currSet: MutableSet<String>, prevLayer: Int): Boolean {
            //for each index of currSet, make a new set with the item at that index removed.
            //if there is not an equivalent set on the previous layer, prune the set

            val currItems: List<String> = currSet.toList()
            outerLoop@ for (i in 0..<currItems.size) {
                //Cannot index into sets so this is the easiest way to remove an nth item from a sorted set
                val itemToRemove = currItems[i]
                val newSet = currSet.toSortedSet()
                newSet.remove(itemToRemove)

                //compare each newSet to a set in the current layer. When one is found, continue
                for (p in 0..<aprioriArrays[prevLayer].size) {
                    if (newSet.equals(aprioriArrays[prevLayer][p].assoc.items)) {
                        continue@outerLoop //continue the outerLoop
                    }
                }
                return false //example was found that was not present in previous layer
            }
            return true //all examples were found at previous layer
        }

        //Support counting function (brute force)
        fun countSupport(currSet: MutableSet<String>): Int {
            //For the given candidate check how many transactions contain all elements in the candidate
            var count = 0
            for (itemSet in marketSets) {
                if (itemSet.containsAll(currSet)) {
                    count++
                }
            }
            return count
        }


        currLevel = 0 //the current level which is being used to generate the next level
        do {
            val candidatesPruned: ArrayList<MutableSet<String>> = arrayListOf() //pruned candidates list

            //Create a structure to store the associations for the current row
            val newAssocList: ArrayList<Association> = arrayListOf() //the structure to store the next associations

            print("generate candidates: ")
            var time = System.currentTimeMillis()

            //for each item in the current level of the aprioriTree, generate itemsets, prune them
            for (i in 0..<aprioriArrays[currLevel].size) {
                val currNode = aprioriArrays[currLevel][i] //the current node which is being used to generate candidates
                for (p in i+1..<aprioriArrays[currLevel].size) {
                    val opNode = aprioriArrays[currLevel][p] //the current op which is being used to generate candidates
                    val candidate: MutableSet<String> =
                        GenFreqItemSets(currNode, opNode) ?: continue //if no combo exists

                    if (PruneItemSet(candidate,currLevel)) //If the candidate passes the prune test, add it to candidatesPruned
                        candidatesPruned.add(candidate)
                }
            }
            println("${ System.currentTimeMillis() - time } ms") //time to count generation

            //Count the support of each candidate. if the support is greater than minSup, make it an association and add
            //it to the aprioriTree
            print("count support: ")
            time = System.currentTimeMillis()
            for (cand in candidatesPruned) {
                val count = countSupport(cand) //takes AWHILE
                if (count/Association.num.toDouble() >= minsup) {
                    val newAssoc = Association(cand, count)
                    newAssocList.add(newAssoc)
                }
            }
            println("${ System.currentTimeMillis() - time } ms") //time to count support

            //Add the new layer
            aprioriArrays.add(ArrayList())
            for ((ind, assoc) in newAssocList.withIndex()) {
                aprioriArrays.last().add(Node(assoc, Pair(currLevel + 1, ind)))
            }

            outputAssoc(currLevel, file)
            printTreeLevel(currLevel)
            currLevel++ //Ready for the next level
        } while (newAssocList.isNotEmpty()) //if there is no new supported associations on the last layer

        //Step 3.
        //Generate rules for each association in the apriori tree

        for(i in 0..< aprioriArrays.size)
        {
            for(p in 0..< aprioriArrays[i].size)
            {
                aprioriArrays[i][p].assoc.GenConfRules(minconf, aprioriArrays)
            }
        }
        val file = File(RULES_OUT)
        file.writeText("")
        file.appendText("\n\nRULES:\n")
        for(i in 0..<currLevel){ outputRules(i, file) }
        for(i in 0..<currLevel){ printRulesLevel(i) }
    }

    //Print the rules for a given level
    fun printRulesLevel(level: Int)
    {
        for(i in 0..< aprioriArrays[level].size)
        {
            if(aprioriArrays[level][i].assoc.rules.size > 0 && aprioriArrays[level][i].assoc.rules[0].size > 0)
                println("\trules for ${aprioriArrays[level][i].assoc.items}")
            for(j in 0..< aprioriArrays[level][i].assoc.rules.size)
            {
                for(k in 0..< aprioriArrays[level][i].assoc.rules[j].size)
                {
                    println("\t\t${aprioriArrays[level][i].assoc.rules[j][k].left}->${aprioriArrays[level][i].assoc.rules[j][k].right}")
                }
            }
        }
    }

    fun outputRules(level: Int, file: File)
    {
        for(i in 0..< aprioriArrays[level].size)
        {
            if(aprioriArrays[level][i].assoc.rules.size > 0 && aprioriArrays[level][i].assoc.rules[0].size > 0)
                file.appendText("\trules for ${aprioriArrays[level][i].assoc.items}\n")
            for(j in 0..< aprioriArrays[level][i].assoc.rules.size)
            {
                for(k in 0..< aprioriArrays[level][i].assoc.rules[j].size)
                {
                    file.appendText("\t\t${aprioriArrays[level][i].assoc.rules[j][k].left}->${aprioriArrays[level][i].assoc.rules[j][k].right}\n")
                }
            }
        }
    }

    //prints a given level of the tree
    fun printTreeLevel(level: Int)
    {
        println("Level $level")
        for(i in 0..< aprioriArrays[level].size)
        {
            println("\t${aprioriArrays[level][i].assoc.items}")
        }
    }

    fun outputAssoc(level: Int, file: File)
    {
        file.appendText("Level $level\n")
        for(i in 0..< aprioriArrays[level].size)
        {
            file.appendText("\t${aprioriArrays[level][i].assoc.items}\n")
        }
    }

    fun printAtomicFreq()
    {
        for(atomic in atomics)
        {
            println(atomic.first + ": " + atomic.second.toString())
        }
    }

    //Take all the transactions from marketMap and add them to marketSets
    fun prepareMarketSets()
    {
        val keys = get_keys()
        for(key in keys)
        {
            marketSets.add(sortedSetOf())
            for(item in marketMap[key]!!)
            {
                marketSets.last().add(item.ticker)
            }
        }
    }

    //Adds the ticker and market to the map with the corresponding dateKey
    private fun add_to_map(key: Int, newItem: OHLCDate)
    {
        //If the hashmap does not yet contain the key, add an empty set
        if(!marketMap.containsKey(key))
            marketMap[key] = mutableSetOf()

        //add item to the set in the
        marketMap[key]!!.add(newItem)
    }

    //Returns an array of the keys
    fun get_keys(): Array<Int>
    {
        return marketMap.keys.toIntArray().toTypedArray()
    }

    //Adds all csv files from all subdirectories of the passed path which meet the following conditions
    //1) A destination along the path is not named "exclude"
    //2) The destination containing .csv files is not the ancestor of another destination containing csv files
    //
    //Only csv files which pass the is_valid test will be added
    fun scrape_add(topTree: String /*the parent path which will be searched recursively*/)
    {
        var time = System.currentTimeMillis()

        //walk through the file tree
        //From the passed directory into every subdirectory
        var dir = File(topTree)
        var firstFile = true
        var marketName = "MARKET_DEFAULT" //Variable to hold the current market name
        File(topTree).walk().forEach {
            //walk until reaching the first file
            //println(it)
            if(it.isFile) {

                //validate the file, if invalid, print error and continue
                //print("file ${it.name}\n")
                if (!is_valid(it)) {
                    //print("skip! invalid file ${it.name}\n")
                    return@forEach
                }

                //file is valid, add all contents
                //print("File is valid\n\n")

                //If this is the first file in the directory,
                //Ask the user for a market name.
                //If they type 'skip,' skip this dir
                if(firstFile) {
                    //time = System.currentTimeMillis() - time
                    //println(time)
                    print("What is the name of the market found at directory '${dir.path}' : ")
                    val input = Scanner(System.`in`)
                    time = System.currentTimeMillis() - time
                    marketName = input.nextLine()
                    if (marketName.lowercase() == "skip") {
                        //Skip the directory by walking until

                        while (it.startsWith(dir)) {
                            try {
                                it.walk()
                            } catch (e: FileSystemLoopException) {
                                //If a file is found in the first possible directory, which is also the final
                                //child directory, and the user enters 'skip,'
                                //to skip that directory, this code will loop forever because all reachable directories
                                //start with the base directory.
                                //This is a stupid solution for a stupid problem, as this case should always return without
                                //adding a single ticker to the list anyway.
                                print("File system loop detected!")
                                return
                            }
                            if (it.isDirectory) {
                                //When the next directory is reached, set dir to the new directory, ser first file,
                                //and continue.
                                dir = it
                                firstFile = true
                                return@forEach
                            }
                        }
                    }
                    //Check if a new market needs to be allocated
                    firstFile = false
                }
                //Get the ticker name from the filename
                //If the existsMap already contains the ticker, continue
                val tickerName = it.nameWithoutExtension

                val reader = it.bufferedReader()
                var currLine = reader.readLine() //skip first header line
                currLine = reader.readLine()

                var check = false
                var count = 1
                //marketSets.add(sortedSetOf())
                while (currLine != null) //may be a bad string comparison?
                {
                    //split the line of the file into it's core components
                    //There must be exactly 7
                    //print("Adding $tickerName: '$currLine'\n")
                    val args = currLine.split(",")


                    //If any element in the chainlist has this tickername already, a file with data for this ticker was
                    //already parsed; skip!
                    if (!check &&
                        marketMap[date_to_key(args[0])] != null &&
                        marketMap[date_to_key(args[0])]!!.any { obj -> obj?.ticker.equals(tickerName) })
                    {
                        //print("$tickerName already exists in MarketList\n")
                        return@forEach
                    }
                    else
                        check = true


                    // Add the current line into the map
                    //if the low and high values are doubles, set newHigh and newLow. if not, continue
                    val date: Int = date_to_key(args[0])
                    if(date == -1) {currLine = reader.readLine(); continue}

                    val open: Float = try{args[2].toFloat()}catch(e: NumberFormatException){currLine = reader.readLine(); continue}
                    //val high: Float = try{args[4].toFloat()}catch(e: NumberFormatException){currLine = reader.readLine(); continue}
                    //val low: Float = try{args[1].toFloat()}catch(e: NumberFormatException){currLine = reader.readLine(); continue}
                    val close: Float = try{args[5].toFloat()}catch(e: NumberFormatException){currLine = reader.readLine(); continue}

                    //delete all tickers that did not see positive returns
                    if(close < open) {currLine = reader.readLine(); continue}

                    val volume: Int =  try{args[3].toInt()}catch(e: NumberFormatException){currLine = reader.readLine();  continue}
                    //val adjClose: Float = try{args[6].toFloat()}catch(e: NumberFormatException){currLine = reader.readLine(); continue}
                    //println(tickerName)

                    //val newItem = OHLCDate(marketName, tickerName, open, high,low,close,volume,adjClose)
                    val newItem = OHLCDate(marketName, tickerName, open,close,volume)

                    add_to_map(date,newItem)


                    //marketSets.last().add(tickerName)

                    count++
                    //Read next line and continue
                    currLine = reader.readLine()
                }
                if(count != 1) { //Hard code it
                    //val atomic = Association(setOf(tickerName))
                    val atomic = Pair(tickerName, count)
                    atomics.add(atomic)
                }
            }
            else
            {
                //Set dir to the current deepest non-file
                //If topTree was the directory to a file, this will be a file instead.
                dir = it

                //firstFile keeps track of when the first file for a new directory is being parsed
                firstFile = true

                //print("dir = ${it.name}\n")
            }

        }
    }

    //checks if a given file is valid based on the following conditions:
    //1) The file is a csv file
    //2) The first two lines of the file exist
    //3) The first line is identical to the HEADER constant
    //4) The first column on the second line has data which can be converted to a valid int from the date_to_key function
    //5) The second line of the file contains as many DELIMITER characters as NUM_COLS-1
    //Precond: The passed file is a .csv file
    fun is_valid(file: File /*The file to be judged*/): Boolean
    {
        //Must be csv file, else invalid
        if(!file.extension.equals("csv"))
            return false


        //print("w\n")
        //println("${file.extension}")
        var reader = file.bufferedReader()

        //Get the first line. If it is not equal to HEADER, the file is invalid
        var line1 = reader.readLine()
        if(!line1.equals(HEADER)) {
            //println("the first\n")
            return false
        }

        //Get the second line and iterate through it. If it does not contain NUM_COLS-1 DELIMETERS, the file is invalid
        var line2 = reader.readLine()
        var numDelim = 0 //The number of delimiter characters found in line2
        var currIndex = 0
        while(currIndex != -1 && numDelim <= NUM_COLS)
        {
            currIndex = line2.indexOf(',',currIndex+1)
            numDelim++
        }

        //numDelim will be 1+the number of delimiters
        if(numDelim!=NUM_COLS) {
            //print("$numDelim\n")
            return false;
        }
        //If the first data point on the second line cannot be converted to a date, the file is invalid
        val dateStr = line2.substring(0..<line2.indexOf(','))
        //println("you")
        return date_to_key(dateStr) != -1;
    }


    /*
    //Returns true if the passed line contains valid data
    fun validate_line(line: String): Boolean
    {
        //get arguments
        val args = line.split(",")

        //convert each string component into the desired value, and create the OHLC_Date object
        val dateKey = date_to_key(args[0])
        if(dateKey == -1) {
            return false //Do not add a record with an invalid dateKey
        }
    }
    */


    //Takes a date in the format DDXMMXYYYY and coverts it to an integer of the format YYYYMMDD,
    //returns the result, or -1 if the passed string is not a valid date
    companion object {
        fun date_to_key(date_str: String): Int {
            //If the len of date_str is not DATE_STR_SIZE, it is invalid
            if (date_str.length != DATE_STR_SIZE) {
                return -1
            }

            //Grab the first 2 characters in the date string, which represent the day portion of the date.
            //If the first 2 characters do not convert to an integer, the date string is invalid, so return -1
            val day: Int = try {
                (date_str.substring(0..1)).toInt()
            } catch (e: NumberFormatException) {
                return -1
            }

            //Does the same for the month
            val month: Int = try {
                (date_str.substring(3..4)).toInt()
            } catch (e: NumberFormatException) {
                return -1
            }

            //Does the same for the year
            val year: Int = try {
                (date_str.substring(6..9)).toInt()
            } catch (e: NumberFormatException) {
                return -1
            }

            //Combine the day, month, and year values to create the key and return it
            return day + 100 * month + 10000 * year
        }
    }
}