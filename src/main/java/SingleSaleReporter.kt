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
private val HEADER = listOf("Image", "Link", "Time of purchase", "Translated title", "Price")

class SingleSaleReporter(private val referenceSpreadsheetId: String, private val spreadsheetToUpdateId: String) {

  fun report() {
    val spreadsheets = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
        .setApplicationName(APPLICATION_NAME)
        .build()
        .spreadsheets()

    val title = (spreadsheets.get(spreadsheetToUpdateId)
        .execute()
        .values.asSequence().first() as SpreadsheetProperties)["title"]

    val allExportedValues = getValuesAlreadyPresentInSpreadsheet(spreadsheets)

    if (!allExportedValues.containsKey(title)) {
      return
    }

    val titlesToSheetIds = getTitlesToSheetIds(spreadsheets, allExportedValues, title)

    allExportedValues[title]!!.forEach {
      clearSheet(spreadsheets, spreadsheetToUpdateId, titlesToSheetIds[it.key])
      addValuesToSheet(spreadsheets, spreadsheetToUpdateId, "%s%s".format(it.key, "!A1:B"), listOf(listOf("Total", "=SUM(E:E)")))
      createHeader(spreadsheets, spreadsheetToUpdateId, "%s%s".format(it.key, RANGE), HEADER)
      addValuesToSheet(spreadsheets, spreadsheetToUpdateId, "%s%s".format(it.key, RANGE), it.value)
      resizeCells(spreadsheets, spreadsheetToUpdateId, titlesToSheetIds[it.key], 2)
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
        .forEach { createSheet(spreadsheets, spreadsheetToUpdateId, it) }

    if (allExportedValues[title]!!.isEmpty()) {
      return titlesToSheetIds
    }

    // New sheets were created, return an up-to-date list
    return getAllSheetTitles(spreadsheets)
  }

  private fun getAllSheetTitles(spreadsheets: Sheets.Spreadsheets): Map<String, Int> {
    return ((spreadsheets.get(spreadsheetToUpdateId)
        .setFields("sheets.properties")
        .execute()
        .values
        .single() as ArrayList<Sheet>)
        .map { it["properties"] } as ArrayList<SheetProperties>)
        .map { it["title"].toString() to Integer.valueOf(it["sheetId"].toString()) }
        .toMap()
  }


  private fun getValuesAlreadyPresentInSpreadsheet(spreadsheets: Sheets.Spreadsheets):
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
          map.getOrPut(it[6].toString()) {  HashMap() }.getOrPut(it[7].toString()) { mutableListOf() }.add(it.subList(0, 5))
        }

    return map
  }
}
