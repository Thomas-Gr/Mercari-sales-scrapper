package scanner

import utils.SheetsCredentialProvider.getCredentials
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import common.MercariData
import utils.addValuesToSheet
import utils.createHeader
import utils.resizeCells
import java.time.format.DateTimeFormatter

private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
private const val APPLICATION_NAME = "Mercari sales scrapper"
private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
private const val RANGE = "Data!A1:H"
private val HEADER = listOf("Image", "Link", "Time of purchase", "Translated title", "Price", "State")
private  const val DONE = "Done"
private val JAPANESE_TIME_FORMATTER = DateTimeFormatter.ofPattern("YYYY年M月D日 H:mm")
private val SHEET_TIME_FORMATTER = DateTimeFormatter.ofPattern("DD/MM HH:mm")

class SheetsExporter(private val spreadsheetId: String, private val sort: Boolean = true) {

  fun toSheet(data: Collection<MercariData>) {
    val spreadsheets = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
        .setApplicationName(APPLICATION_NAME)
        .build()
        .spreadsheets()

    val alreadyExportedValues = getValuesAlreadyPresentInSpreadsheet(spreadsheets)

    createHeader(spreadsheets, spreadsheetId, RANGE, HEADER)
    updateUpdatedData(data, alreadyExportedValues, spreadsheets)
    appendNewValues(spreadsheets, data.filter { !alreadyExportedValues.containsKey(extractKey(it.link)) })
    resizeCells(spreadsheets, spreadsheetId, 0, 1)
    if (sort) {
      sort(spreadsheets)
    }
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
        .drop(1)
        .filter { it.size >= 5 }
        .mapIndexed { i, value -> extractKey(value[1].toString()) to Pair(i, value[5]) }
        .toMap()
  }

  private fun updateUpdatedData(
    data: Collection<MercariData>,
    alreadyExportedValues: Map<String, Pair<Int, Any>>,
    spreadsheets: Sheets.Spreadsheets) {
    val dataThatWasNotExportedWithObsoleteState = data
        .filter { alreadyExportedValues.containsKey(extractKey(it.link)) }
        .filter { alreadyExportedValues.getValue(extractKey(it.link)).second != DONE && it.isDone }
        .map {
          Request().setUpdateCells(
              UpdateCellsRequest()
                  .setRange(GridRange().setSheetId(0)
                      .setStartColumnIndex(5)
                      .setEndColumnIndex(6)
                      .setStartRowIndex(alreadyExportedValues.getValue(extractKey(it.link)).first)
                      .setEndRowIndex(alreadyExportedValues.getValue(extractKey(it.link)).first + 1))
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

    addValuesToSheet(spreadsheets, spreadsheetId, RANGE, values)
  }

  private fun extractKey(value: String) = value.substring(value.length - 13, value.length)
}
