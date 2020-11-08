package fb

import common.SPREADSHEET_FB
import fb.Helper.generateShortMessage
import fb.Helper.getValuesAlreadyPresentInSpreadsheet

fun main() {
  val job = PriceSummarizer(SPREADSHEET_FB)

  job.start()
}

class PriceSummarizer(private val spreadsheetId: String) {

  fun start() {
    val items = getValuesAlreadyPresentInSpreadsheet(spreadsheetId)

    val map = HashMap<String, MutableList<Pair<String, Double>>>()

    for (item in items.filter { it.size > 8 }) {
      map.getOrPut(item[8].toString()) { mutableListOf() }
          .add(Pair(generateShortMessage(item), asNumber(item[4].toString())))

      if (item.size > 9) {
        map.getOrPut(item[9].toString()) { mutableListOf() }
            .add(Pair(generateShortMessage(item), asNumber(item[4].toString())))
      }
    }

    map.entries.forEach { entry ->
      println(entry.key)
      println("Total : %.2f €".format(entry.value.map { it.second }.sum()))
      entry.value.forEach { println(it.first)}

      println("")
    }
  }

  private fun asNumber(value: String): Double {
    return Regex("([0-9]+),([0-9]+)")
        .findAll(value)
        .map { it.groupValues[1].toInt() + it.groupValues[2].toDouble() / 100 }
        .first()
  }
}
