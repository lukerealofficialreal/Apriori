import java.util.*

data class Rule(
    val left: MutableSet<String>,
    val right: MutableSet<String>

    //left -> right
)

/*
This class represents all the associations for a given year

an Association is a subset of tickers which occur together
 */
data class Association (
    val items: MutableSet<String>, //Maintained sorted to allow for easier comparisons between sets
    val supportCount: Int = 0
){
    //rules should only contain rules which are deemed confident
    val rules: ArrayList<ArrayList<Rule>> = arrayListOf(arrayListOf())

    //val items: Set<OHLCDate> //the sey of items which are associated at least once
    val numElements: Int = items.size //The number of associated elements


    val support: Double //This allows the support to be accessed like as a member instead of a function
        get() = supportCount / num.toDouble()

    //the number of total transactions MUST be set before using this class value
    companion object Transactions{
        var num = 1 //initial value
    }


    //Generates all rules for this association
    fun GenConfRules(minconf: Double, aprioriTree: ArrayList<ArrayList<Node>>)
    {
        var ruleLevel = 0

        //generate atomic rules (right size = 1)
        var currItems: List<String> = items.toList()
        for (i in 0..< items.size - ruleLevel) {
            //Cannot index into sets so this is the easiest way to remove an nth item from a sorted set
            val itemToRemove = currItems[i]
            val newSet = items.toMutableSet()
            newSet.remove(itemToRemove)

            //If newSet has no elements, there are no remaining rules
            if(newSet.size == 0)
                return

            val atomicRule = Rule(newSet, mutableSetOf(itemToRemove))



            //add the rule to the rules if it meets the confidence requirement
            // (left U right) / (left)  >= minconf
            var leftSupportCount = 0
            for(p in 0..< aprioriTree[newSet.size-1].size)
            {
                if(aprioriTree[newSet.size-1][p].assoc.items.equals(newSet)) {
                    leftSupportCount = aprioriTree[newSet.size - 1][p].assoc.supportCount
                    break
                }
            }

            if(leftSupportCount > 0 && (supportCount / leftSupportCount.toDouble()) >= minconf)
                rules[ruleLevel].add(atomicRule)

        }

        ruleLevel++

        do{
            var itemsAdded = false
            rules.add(arrayListOf())

            //generate new rules (right > 1)
            for (i in 0..< rules[ruleLevel-1].size) {
                val currParent = rules[ruleLevel-1][i]
                for(p in 0..< items.size - ruleLevel) {
                    currItems = currParent.left.toList()
                    val itemToRemove = currItems[p]
                    val newSet = currItems.toMutableSet()
                    val rightSet = currParent.right.toMutableSet()
                    rightSet.add(itemToRemove)
                    newSet.remove(itemToRemove)

                    //If newSet has no elements, there are no remaining rules
                    if(newSet.size == 0)
                        return

                    val newRule = Rule(newSet,rightSet)

                    //add the rule to the rules if it meets the confidence requirement
                    // (left U right) / (left)  >= minconf
                    var leftSupportCount = 0
                    for (j in 0..<aprioriTree[newSet.size-1].size) {
                        if (aprioriTree[newSet.size-1][j].assoc.items.equals(newSet))
                            leftSupportCount = aprioriTree[newSet.size-1][j].assoc.supportCount
                    }

                    if (leftSupportCount > 0 && (supportCount / leftSupportCount.toDouble()) >= minconf) {
                        rules[ruleLevel].add(newRule)
                        itemsAdded = true
                    }
                }
            }
            ruleLevel++
        }while(itemsAdded && ruleLevel < items.size)

    }

}