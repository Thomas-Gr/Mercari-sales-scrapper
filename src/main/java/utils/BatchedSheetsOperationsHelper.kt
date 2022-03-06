package utils

import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*
import com.google.common.util.concurrent.RateLimiter

val rateLimiter = RateLimiter.create(.5)

fun executeBatchedRequests(spreadsheets: Sheets.Spreadsheets, spreadsheetId: String?, requests: List<Request>) {
  rateLimiter.acquire()
  println("executeBatchedRequests")
  spreadsheets.batchUpdate(
      spreadsheetId,
      BatchUpdateSpreadsheetRequest().setRequests(requests))
      .execute()
}

fun batchedCreateSheet(sheetName: String): Request {
  return Request().setAddSheet(
      AddSheetRequest()
          .setProperties(SheetProperties().setTitle(sheetName)))
}

fun batchedClearSheet(sheetId: Int?): Request {
  return Request().setUpdateCells(
      UpdateCellsRequest()
          .setRange(GridRange().setSheetId(sheetId))
          .setFields("userEnteredValue"))
}

fun batchedResizeCells(sheetId: Int?, startRow: Int): Request {
  return Request().setUpdateDimensionProperties(
      UpdateDimensionPropertiesRequest()
          .setRange(DimensionRange().setSheetId(sheetId).setDimension("ROWS").setStartIndex(startRow))
          .setProperties(DimensionProperties().setPixelSize(100))
          .setFields("pixelSize"))
}

fun batchedAlternatedColors(sheetId: Int?, startRow: Int, endRow: Int, startColumn: Int, endColumn: Int): Request {
  return Request().setAddBanding(
      AddBandingRequest().setBandedRange(
          BandedRange()
              .setBandedRangeId(1)
              .setRange(GridRange()
                  .setSheetId(sheetId)
                  .setStartRowIndex(startRow)
                  .setEndRowIndex(endRow)
                  .setStartColumnIndex(startColumn)
                  .setEndColumnIndex(endColumn))
              .setRowProperties(BandingProperties()
                      .setHeaderColor(Color().setRed(0.6f).setGreen(0.6f).setBlue(0.6f))
                      .setFirstBandColor(Color().setRed(0.8f).setGreen(0.8f).setBlue(0.8f))
                      .setSecondBandColor(Color().setRed(0.9f).setGreen(0.9f).setBlue(0.9f)))))
}

fun batchedCurrency(sheetId: Int?, column: Int): Request {
  return Request()
      .setRepeatCell(RepeatCellRequest()
          .setRange(GridRange().setSheetId(sheetId).setStartColumnIndex(column))
          .setCell(CellData().setUserEnteredFormat(
              CellFormat().setNumberFormat(NumberFormat().setType("CURRENCY"))))
          .setFields("userEnteredFormat.numberFormat"))
}

fun batchedImportant(sheetId: Int?, startRow: Int, endRow: Int, startColumn: Int, endColumn: Int): Request {
 return Request()
      .setRepeatCell(RepeatCellRequest()
          .setRange(GridRange().setSheetId(sheetId)
              .setStartRowIndex(startRow)
              .setEndRowIndex(endRow)
              .setStartColumnIndex(startColumn)
              .setEndColumnIndex(endColumn))
          .setCell(CellData().setUserEnteredFormat(
              CellFormat()
                  .setBackgroundColor(Color().setRed(1f).setGreen(0f).setBlue(0f))
                  .setTextFormat(TextFormat().setBold(true))))
          .setFields("*"))
}