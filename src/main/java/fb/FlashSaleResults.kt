package fb

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.SheetProperties
import common.FB_SALES_RESULTS_ID
import common.SPREADSHEET_FB
import fb.Helper.generateShortMessage
import fb.Helper.getValuesAlreadyPresentInSpreadsheet
import utils.*

private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
private const val APPLICATION_NAME = "Mercari sales scrapper"
private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
private const val RANGE = "!A2:H"
private val HEADER = listOf("Numéro du lot", "Image", "Lien", "Description", "Set", "Etat", "Prix")

fun main() {
  val job = FlashSaleResults(SPREADSHEET_FB, FB_SALES_RESULTS_ID)

  job.start()
}

class FlashSaleResults(private val spreadsheetIdInput: String, private val spreadsheetIdOutput: String) {

  fun start() {
    val sales = getSalesTotal()

    val spreadsheets = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, SheetsCredentialProvider.getCredentials(HTTP_TRANSPORT))
        .setApplicationName(APPLICATION_NAME)
        .build()
        .spreadsheets()

    val allSheetTitles = getAllSheetTitles(spreadsheets)

    allSheetTitles.keys.forEach { println(it) }

    sales.entries.forEach {
      run {
        val lines = it.value.map {
          if (isVideo(it)) {
            listOf(it[6], "VIDEO", "VIDEO", it[1], it[2], it[3], it[4])
          } else {
            listOf(it[6], "=IMAGE(\"https://www.grilletta.fr/sales_merged/${it[0]}.jpg\")", "=HYPERLINK(\"https://www.grilletta.fr/sales_merged/${it[0]}.jpg\"; \"LIEN\")", it[1], it[2], it[3], it[4])
          }
        }

        println(lines)
        if (allSheetTitles.containsKey(it.key)) {
          clearSheet(spreadsheets, spreadsheetIdOutput, allSheetTitles[it.key])
          resizeCells(spreadsheets, spreadsheetIdOutput, allSheetTitles[it.key], 2)
        } else {
          createSheet(spreadsheets, spreadsheetIdOutput, it.key)
        }

        addValuesToSheet(spreadsheets, spreadsheetIdOutput, "%s%s".format(it.key, "!A1:C"), listOf(listOf("Prix cartes", "=SUM(G2:G)", "Prix fdp", "", "", "Total", "=B1+D1")))
        createHeader(spreadsheets, spreadsheetIdOutput, "%s%s".format(it.key, RANGE), HEADER)
        addValuesToSheet(spreadsheets, spreadsheetIdOutput, "%s%s".format(it.key, RANGE), lines)
      }
    }
  }

  private fun isVideo(it: List<Any>) = false

  private fun getAllSheetTitles(spreadsheets: Sheets.Spreadsheets): Map<String, Int> {
    return ((spreadsheets.get(spreadsheetIdOutput)
        .setFields("sheets.properties")
        .execute()
        .values
        .single() as ArrayList<Sheet>)
        .map { it["properties"] } as ArrayList<SheetProperties>)
        .map { it["title"].toString() to Integer.valueOf(it["sheetId"].toString()) }
        .toMap()
  }

  private fun getSalesTotal(): HashMap<String, MutableList<List<Any>>> {
    val items = getValuesAlreadyPresentInSpreadsheet(spreadsheetIdInput)

    val sales = HashMap<String, MutableList<List<Any>>>()

    for (item in items.filter { it.size > 8 }) {
      sales.getOrPut(item[8].toString()) { mutableListOf() }.add(item)

      if (item.size > 9) {
        sales.getOrPut(item[9].toString()) { mutableListOf() }.add(item)
      }
    }

    return sales
  }

  private fun getSales(): HashMap<String, MutableList<Pair<String, Double>>> {
    val items = getValuesAlreadyPresentInSpreadsheet(spreadsheetIdInput)

    val map = HashMap<String, MutableList<Pair<String, Double>>>()

    for (item in items.filter { it.size > 8 }) {
      map.getOrPut(item[8].toString()) { mutableListOf() }.add(Pair(generateShortMessage(item), asNumber(item[4].toString())))

      if (item.size > 9) {
        map.getOrPut(item[9].toString()) { mutableListOf() }.add(Pair(generateShortMessage(item), asNumber(item[4].toString())))
      }
    }
    return map
  }

  private fun asNumber(value: String): Double {
    return Regex("([0-9]+),([0-9]+)")
        .findAll(value)
        .map { it.groupValues[1].toInt() + it.groupValues[2].toDouble() / 100 }
        .first()
  }
}
