package toritsu_server

import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import com.amazonaws.services.dynamodbv2.model.ReturnValue
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import utils.SheetsCredentialProvider
import utils.updateValuesToSheet

private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
private const val APPLICATION_NAME = "Mercari sales updater"
private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
private const val RANGE = "!A2:N"
private val TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd H:mm:ss z")

class ItemsUpdater(private val spreadsheetId: String) {
  fun update() {
    val spreadsheets = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, SheetsCredentialProvider.getCredentials(HTTP_TRANSPORT))
      .setApplicationName(APPLICATION_NAME)
      .build()
      .spreadsheets()

    val valuesAlreadyPresentInSpreadsheet = getValuesAlreadyPresentInSpreadsheet(spreadsheets)
    val localDate = ZonedDateTime.now(ZoneId.of("UTC+1"))

    valuesAlreadyPresentInSpreadsheet
      .filter { localDate.isAfter(toZonedDateTime(it[0])) && localDate.isBefore(toZonedDateTime(it[1])) }
      .forEach {
        if (it[12] == "READY") {
          createItem(it)
        } else if (it[12] == "UPDATE") {
          updateItem(it)
        }

        updateValuesToSheet(spreadsheets, spreadsheetId, "Data!M%s:M%s".format(it[14], it[14]), listOf(listOf("DONE")))

        println("Item updated: " + it[4])
      }
  }

  private fun toZonedDateTime(date: String) =
    ZonedDateTime.parse(date.replace("UTC+1", "GMT+01:00"), TIME_FORMATTER)

  private fun createItem(item: List<String>) {
    val endDate = toZonedDateTime(item[1])

    val dbItem = Item()
      .withPrimaryKey("itemId", item[3])
      .withString("index", "1")
      .withString("name", item[4])
      .withString("image", item[11])
      .withString("price", item[5])
      .withInt("endDate", endDate.toEpochSecond().toInt())
      .withString("weight", item[6])
      .withString("size", item[7])
      .withString("nbCards", item[8])
      .withInt("amountAvailable", item[9].toInt())
      .withInt("amountSold", 0)
      .withBoolean("isDirectSale", true)

    dynamoDB.getTable("special_sales").putItem(dbItem)
  }

  private fun updateItem(item: List<String>) {
    val endDate = toZonedDateTime(item[1])

    val updateItemSpec = UpdateItemSpec()
      .withPrimaryKey("itemId", item[3], "index", "1")
      .withUpdateExpression("set endDate = :endDate, amountAvailable = :amountAvailable")
      .withValueMap(ValueMap()
                      .withNumber(":endDate", endDate.toEpochSecond().toInt())
                      .withNumber(":amountAvailable", item[9].toInt())
      )
      .withReturnValues(ReturnValue.UPDATED_NEW)

    dynamoDB.getTable("special_sales").updateItem(updateItemSpec)
  }

  private fun getValuesAlreadyPresentInSpreadsheet(spreadsheets: Sheets.Spreadsheets): List<List<String>> {
    var i = 1
    return spreadsheets.values()
      .get(spreadsheetId, RANGE)
      .execute()
      .getValues()
      .asSequence()
      .onEach { i++ }
      .filter { it.size == 14 }
      .filter { it[12] == "READY" || it[12] == "UPDATE" }
      .map { it.map { value -> value.toString() } + i.toString() }
      .toList()
  }

}
