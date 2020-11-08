package utils

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*

fun addValuesToSheet(spreadsheets: Sheets.Spreadsheets, spreadsheetId: String?, range: String?, data: List<List<*>>) {
  spreadsheets
      .values()
      .append(
          spreadsheetId,
          range,
          ValueRange().setMajorDimension("ROWS").setValues(data))
      .setValueInputOption("USER_ENTERED")
      .execute()
}

fun createSheet(spreadsheets: Sheets.Spreadsheets, spreadsheetId: String?, sheetName: String) {
  spreadsheets.batchUpdate(
      spreadsheetId,
      BatchUpdateSpreadsheetRequest()
          .setRequests(listOf(Request().setAddSheet(
              AddSheetRequest()
                  .setProperties(SheetProperties().setTitle(sheetName))))))
      .execute()
}

fun clearSheet(spreadsheets: Sheets.Spreadsheets, spreadsheetId: String?, sheetId: Int?) {
  spreadsheets.batchUpdate(
      spreadsheetId,
      BatchUpdateSpreadsheetRequest()
          .setRequests(listOf(Request().setUpdateCells(
              UpdateCellsRequest()
                  .setRange(GridRange().setSheetId(sheetId))
                  .setFields("userEnteredValue")))))
      .execute()
}

fun createHeader(spreadsheets: Sheets.Spreadsheets, spreadsheetId: String?, range: String?, header: List<*>) {
  spreadsheets
      .values()
      .update(
          spreadsheetId,
          range,
          ValueRange()
              .setMajorDimension("ROWS")
              .setValues(listOf(header)))
      .setValueInputOption("USER_ENTERED")
      .execute()
}

fun resizeCells(spreadsheets: Sheets.Spreadsheets, spreadsheetId: String?, sheetId: Int?, startRow: Int) {
  spreadsheets.batchUpdate(
      spreadsheetId,
      BatchUpdateSpreadsheetRequest().setRequests(listOf(Request().setUpdateDimensionProperties(
          UpdateDimensionPropertiesRequest()
              .setRange(DimensionRange().setSheetId(sheetId).setDimension("ROWS").setStartIndex(startRow))
              .setProperties(DimensionProperties().setPixelSize(100))
              .setFields("pixelSize")))))
      .execute()
}