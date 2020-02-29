import SheetsCredentialProvider.getCredentials
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import com.google.api.services.sheets.v4.model.*

private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
private const val APPLICATION_NAME = "Mercari sales scrapper"
private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
private const val RANGE = "Data!A1:F"
private val HEADER = listOf("Image", "Link", "Time of purchase", "Translated title", "Price", "State")

class SheetsExporter(private val spreadsheetId: String) {
    fun toSheet(data: Collection<MercariData>) {
      val values = data.map {
        listOf(
            "=image(\"${it.image}\")",
            it.link,
            it.date,
            "=GOOGLETRANSLATE(\"${it.title}\"; \"auto\"; \"en\")",
            it.price,
            it.state)
      }

      val spreadsheets = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
          .setApplicationName(APPLICATION_NAME)
          .build()
          .spreadsheets()

      createHeader(spreadsheets)
      appendNewValues(spreadsheets, values)
      resizeCells(spreadsheets)
    }

  private fun appendNewValues(spreadsheets: Sheets.Spreadsheets, values: List<List<String>>) {
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
