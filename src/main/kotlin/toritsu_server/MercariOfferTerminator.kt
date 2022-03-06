package toritsu_server

import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import com.amazonaws.services.dynamodbv2.model.ReturnValue
import java.util.Date

/**
 * Terminate mercari items when they are sold out.
 */
class MercariOfferTerminator(val scrapper: Scrapper) {
  fun main() {
    for (itemId in getMercariLiveItems()) {
      val stillAvailable = scrapper.isStillAvailable(itemId)

      if (!stillAvailable) {
        println("[$itemId] Not available anymore")
        markItemAsFinished(itemId)
      } else {
        println("[$itemId] Still available")
      }
    }
  }

  private fun markItemAsFinished(itemId: String) {
    val updateItemSpec = UpdateItemSpec()
      .withPrimaryKey("itemId", itemId, "index", "1")
      .withUpdateExpression("set endDate = :endDate")
      .withValueMap(ValueMap().withNumber(":endDate", (Date().time / 1000).toInt()))
      .withReturnValues(ReturnValue.UPDATED_NEW)

    dynamoDB.getTable("special_sales").updateItem(updateItemSpec)
  }

  private fun getMercariLiveItems(): List<String> {
    val table: Table = dynamoDB.getTable("special_sales")

    val nameMap = mapOf(
      Pair("#isMercariItem", "isMercariItem"),
      Pair("#endDate", "endDate"),
      Pair("#amountSold", "amountSold")
    )
    val valueMap = mapOf(
      Pair(":isMercariItem", true),
      Pair(":amountSold", 0),
      Pair(":endDate", Date().time / 1000)
    )
    val scanSpec = ScanSpec()
      .withFilterExpression("#isMercariItem = :isMercariItem and #endDate > :endDate and #amountSold = :amountSold")
      .withNameMap(nameMap)
      .withValueMap(valueMap)

    return table.scan(scanSpec)
      .map { entry -> entry.get("itemId").toString() }
      .toList()
  }
}