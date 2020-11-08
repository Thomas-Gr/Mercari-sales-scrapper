package fb

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.sheets.v4.Sheets
import utils.SheetsCredentialProvider
import java.io.File

private val JSON_FACTORY = JacksonFactory.getDefaultInstance()
private const val APPLICATION_NAME = "FB sales reader"
private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
private const val RANGE = "Data!A2:J"

object Helper {
  fun getValuesAlreadyPresentInSpreadsheet(spreadsheetId:  String): List<MutableList<Any>> {
    val spreadsheets = Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, SheetsCredentialProvider.getCredentials(HTTP_TRANSPORT))
        .setApplicationName(APPLICATION_NAME)
        .build()
        .spreadsheets()

    return spreadsheets.values()
        .get(spreadsheetId, RANGE)
        .execute()
        .getValues()
        .filter { it.size >= 5 }
        .toList()
  }

  fun hashFileName(id: Int): String {
    return "sale_$id".hashCode().toString()
  }

  fun generateMessage(data: List<Any>, folderName: String, files: List<File>): String {
    val name = data[1]
    val serie = data[2]
    val condition = data[3]
    val price = data[4]
    val quantity =  data[5]
    val id = data[6]
    val question = if (data.size > 7) data[7] else ""

    val fullName = if (serie != "") "$name - $serie" else name
    val priceText =
        if (price == "0" || price == "0,00 €")
          "GRATUIT pour le premier qui répond à la question suivante: $question"
        else if (quantity != "")
          "$price / unité"
        else
          "$price"
    val availability = if (quantity != "") "($quantity dispo)" else ""
    val conditionText = if (condition != "") "($condition)" else ""

    val links =
        if (files.size == 2)
          "\n\nDevant: ${link(folderName, files[0].name)}\nArrière: ${link(folderName, files[1].name)}"
        else
          ""

    return "Lot $id: $fullName $conditionText\nPrix: $priceText $availability $links"
  }

  fun generateShortMessage(data: List<Any>): String {
    val name = data[1]
    val serie = data[2]
    val condition = data[3]
    val price = data[4]
    val id = data[6]

    val fullName = if (serie != "") "$name - $serie" else name
    val priceText = if (price == "0" || price == "0,00 €") "GRATUIT" else "$price"
    val conditionText = if (condition != "") "($condition)" else ""

    return "Lot $id: $fullName $conditionText ($priceText)"
  }

  private fun link(folderName: String, file: String): String {
    return "https://grilletta.fr/sales/$folderName/$file"
  }
}
