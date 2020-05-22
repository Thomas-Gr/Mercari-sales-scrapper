import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.Sheet
import com.google.api.services.sheets.v4.model.SheetProperties
import com.google.api.services.sheets.v4.model.SpreadsheetProperties
import utils.*
import utils.SheetsCredentialProvider.getCredentials

private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
private const val APPLICATION_NAME = "Mercari sales scrapper"
private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
private const val RANGE = "!A2:H"
private val HEADER = listOf("Image", "Link", "Time of purchase", "Translated title", "Price (yen)", "Price (euro)")

class SingleSaleReporter(private val referenceSpreadsheetId: String, private val spreadsheetToUpdateId: Pair<String, Boolean>) {

  fun report() {
    val spreadsheets = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
        .setApplicationName(APPLICATION_NAME)
        .build()
        .spreadsheets()

    val title = (spreadsheets.get(spreadsheetToUpdateId.first)
        .execute()
        .values.asSequence().first() as SpreadsheetProperties)["title"]

    val allExportedValues = getValuesAlreadyPresentInSpreadsheet(spreadsheets, spreadsheetToUpdateId.second)

    if (!allExportedValues.containsKey(title)) {
      return
    }

    val titlesToSheetIds = getTitlesToSheetIds(spreadsheets, allExportedValues, title)

    allExportedValues[title]!!.forEach {
      clearSheet(spreadsheets, spreadsheetToUpdateId.first, titlesToSheetIds[it.key])
      addValuesToSheet(spreadsheets, spreadsheetToUpdateId.first, "%s%s".format(it.key, "!A1:C"), listOf(listOf("Total", "=SUM(E:E)", "=SUM(F:F)")))
      createHeader(spreadsheets, spreadsheetToUpdateId.first, "%s%s".format(it.key, RANGE), HEADER)
      addValuesToSheet(spreadsheets, spreadsheetToUpdateId.first, "%s%s".format(it.key, RANGE), it.value)
      resizeCells(spreadsheets, spreadsheetToUpdateId.first, titlesToSheetIds[it.key], 2)
    }
  }

  private fun getTitlesToSheetIds(
      spreadsheets: Sheets.Spreadsheets,
      allExportedValues: HashMap<String, HashMap<String, MutableList<List<*>>>>,
      title: Any?): Map<String, Int> {

    val titlesToSheetIds = getAllSheetTitles(spreadsheets)

    allExportedValues[title]!!
        .map { it.key }
        .filter { !titlesToSheetIds.containsKey(it) }
        .forEach { createSheet(spreadsheets, spreadsheetToUpdateId.first, it) }

    if (allExportedValues[title]!!.isEmpty()) {
      return titlesToSheetIds
    }

    // New sheets were created, return an up-to-date list
    return getAllSheetTitles(spreadsheets)
  }

  private fun getAllSheetTitles(spreadsheets: Sheets.Spreadsheets): Map<String, Int> {
    return ((spreadsheets.get(spreadsheetToUpdateId.first)
        .setFields("sheets.properties")
        .execute()
        .values
        .single() as ArrayList<Sheet>)
        .map { it["properties"] } as ArrayList<SheetProperties>)
        .map { it["title"].toString() to Integer.valueOf(it["sheetId"].toString()) }
        .toMap()
  }

  private fun getValuesAlreadyPresentInSpreadsheet(spreadsheets: Sheets.Spreadsheets, adaptPriceToMarket: Boolean):
      HashMap<String, HashMap<String, MutableList<List<*>>>> {
    val map = HashMap<String, HashMap<String, MutableList<List<*>>>>()

    spreadsheets.values()
        .get(referenceSpreadsheetId, "Data%s".format(RANGE))
        .setValueRenderOption("FORMULA")
        .setDateTimeRenderOption("FORMATTED_STRING")
        .execute()
        .getValues()
        .filter { it.size >= 8 }
        .forEach {
          map.getOrPut(it[6].toString()) {  HashMap() }
              .getOrPut(it[7].toString()) { mutableListOf() }
              .add(it.subList(0, 5)
                + if (adaptPriceToMarket) {
                  "=%s/IF(DATEVALUE(\"%s\") > DATEVALUE(\"09/03/2020\");(MIN(index(GOOGLEFINANCE(\"EURJPY\"; \"price\"; \"%s\");2;2); 120)-20);100)"
                      .format(it[4], it[2],  it[2])
                } else {
                  "=%s/100".format(it[4])
                })
        }

    return map
  }
}
