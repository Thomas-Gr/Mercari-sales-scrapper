import SheetsCredentialProvider.getCredentials
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import java.time.format.DateTimeFormatter

private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
private const val APPLICATION_NAME = "Mercari sales scrapper"
private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
private const val RANGE = "Data!A1:H"
private val HEADER = listOf("Image", "Link", "Time of purchase", "Translated title", "Price", "State")
private  const val DONE = "Done"
private val JAPANESE_TIME_FORMATTER = DateTimeFormatter.ofPattern("M月D日 HH:mm")
private val SHEET_TIME_FORMATTER = DateTimeFormatter.ofPattern("DD/MM HH:mm")

class SheetsExporter(private val spreadsheetId: String, private val sort: Boolean = true) {

  fun toSheet(data: Collection<MercariData>) {
    val spreadsheets = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
        .setApplicationName(APPLICATION_NAME)
        .build()
        .spreadsheets()

    val alreadyExportedValues = getValuesAlreadyPresentInSpreadsheet(spreadsheets)

    createHeader(spreadsheets)
    updateUpdatedData(data, alreadyExportedValues, spreadsheets)
    appendNewValues(spreadsheets, data.filter { !alreadyExportedValues.containsKey(it.link) })
    resizeCells(spreadsheets)
    sort(spreadsheets)
  }

  private fun sort(spreadsheets: Sheets.Spreadsheets) {
    spreadsheets.batchUpdate(
        spreadsheetId,
        BatchUpdateSpreadsheetRequest().setRequests(listOf(Request().setSortRange(
            SortRangeRequest()
                .setRange(GridRange()
                    .setSheetId(0)
                    .setStartColumnIndex(0)
                    .setStartRowIndex(1))
                .setSortSpecs(listOf(SortSpec().setDimensionIndex(2).setSortOrder("DESCENDING")))))))
        .execute()
  }

  private fun getValuesAlreadyPresentInSpreadsheet(spreadsheets: Sheets.Spreadsheets): Map<String, Pair<Int, Any>> {
    return spreadsheets.values()
        .get(spreadsheetId, RANGE)
        .execute()
        .getValues()
        .filter { it.size >= 5 }
        .mapIndexed { i, value -> value[1].toString() to Pair(i, value[5]) }
        .toMap()
  }

  private fun updateUpdatedData(
      data: Collection<MercariData>,
      alreadyExportedValues: Map<String, Pair<Int, Any>>,
      spreadsheets: Sheets.Spreadsheets) {
    val dataThatWasNotExportedWithObsoleteState = data
        .filter { alreadyExportedValues.containsKey(it.link) }
        .filter { alreadyExportedValues.getValue(it.link).second != DONE && it.isDone }
        .map {
          Request().setUpdateCells(
              UpdateCellsRequest()
                  .setRange(GridRange().setSheetId(0)
                      .setStartColumnIndex(5)
                      .setEndColumnIndex(6)
                      .setStartRowIndex(alreadyExportedValues.getValue(it.link).first)
                      .setEndRowIndex(alreadyExportedValues.getValue(it.link).first + 1))
                  .setFields("userEnteredValue")
                  .setRows(listOf(RowData().setValues(listOf(
                      CellData().setUserEnteredValue(ExtendedValue().setStringValue(DONE)))))))
        }

    if (dataThatWasNotExportedWithObsoleteState.isNotEmpty()) {
      spreadsheets.batchUpdate(
              spreadsheetId,
              BatchUpdateSpreadsheetRequest().setRequests(dataThatWasNotExportedWithObsoleteState))
          .execute()
    }
  }

  private fun appendNewValues(spreadsheets: Sheets.Spreadsheets, dataThatWasNotExportedYet: List<MercariData>) {
    val values = dataThatWasNotExportedYet.map {
      listOf(
          "=image(\"${it.image}\")",
          it.link,
          SHEET_TIME_FORMATTER.format(JAPANESE_TIME_FORMATTER.parse(it.date)),
          "=GOOGLETRANSLATE(\"${it.title}\"; \"auto\"; \"en\")",
          it.price,
          if (it.isDone) "Done" else "In Progress")
    }

    spreadsheets
        .values()
        .append(
            spreadsheetId,
            RANGE,
            ValueRange().setMajorDimension("ROWS").setValues(values))
        .setValueInputOption("USER_ENTERED")
        .execute()
  }

  private fun createHeader(spreadsheets: Sheets.Spreadsheets) {
    spreadsheets
        .values()
        .update(
            spreadsheetId,
            RANGE,
            ValueRange()
                .setMajorDimension("ROWS")
                .setValues(listOf(HEADER)))
        .setValueInputOption("USER_ENTERED")
        .execute()
  }

  private fun resizeCells(spreadsheets: Sheets.Spreadsheets) {
    spreadsheets.batchUpdate(
        spreadsheetId,
        BatchUpdateSpreadsheetRequest().setRequests(listOf(Request().setUpdateDimensionProperties(
            UpdateDimensionPropertiesRequest()
                .setRange(DimensionRange().setSheetId(0).setDimension("ROWS").setStartIndex(1))
                .setProperties(DimensionProperties().setPixelSize(100))
                .setFields("pixelSize")))))
        .execute()
  }
}
