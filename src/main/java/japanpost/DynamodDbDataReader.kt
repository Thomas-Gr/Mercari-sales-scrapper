package japanpost

import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec

class DynamoDbDataReader {
  private val defaultWeight = 680

  fun readData(): List<Data> {
    val client: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
      .withRegion(Regions.EU_WEST_3)
      .build()

    val dynamoDB = DynamoDB(client)

    val table: Table = dynamoDB.getTable("orders_in_progress")

    val nameMap = mapOf(Pair("#order_status", "order_status"))
    val valueMap = mapOf(Pair(":order_status", "READY"))

    val scanSpec = ScanSpec()
      .withFilterExpression("#order_status = :order_status")
      .withNameMap(nameMap)
      .withValueMap(valueMap)


    return table.scan(scanSpec)
      .map { entry ->
        val items = entry.getList<Map<String, String>>("items")
          .map {
            Item(
              "CARD GAME",
              ((it["price"] ?: "").replace(",", ".").toFloat() * 100).toInt(),
              it["quantity"]?.toInt() ?: 1
            )
          }
          .groupBy { it.name }
          .mapValues {
            val quantity = it.value.map { it.quantity }.sum()
            val pricePerUnit = it.value.map { it.unitPrice * it.quantity }.sum() / quantity

            Item(it.key, pricePerUnit, quantity)
          }
          .values
          .toList()


        Data(
          entry.getMap<String>("shipping")["name"] ?: "",
          items,
          defaultWeight,
          Address(
            toCountryName(entry.getMap<String>("shipping")["country"] ?: ""),
            entry.getMap<String>("shipping")["address"] ?: "",
            entry.getMap<String>("shipping")["city"] ?: "",
            entry.getMap<String>("shipping")["postal_code"] ?: ""
          )
        )
      }
      .groupBy { Pair(it.name, it.address) }
      .mapValues {
        val items = it.value.flatMap { it.items }.groupBy { it.name }.mapValues {
          val quantity = it.value.map { it.quantity }.sum()
          val pricePerUnit = it.value.map { it.unitPrice * it.quantity }.sum() / quantity
          Item(it.key, pricePerUnit, quantity)
        }.values.toList()

        Data(it.key.first, items, defaultWeight, it.key.second)
      }.values.toList()
  }

  private fun toCountryName(input: String): String =
      when {
        input.toLowerCase() == "SW" -> "SWITZERLAND"
        input.toLowerCase() == "BE" -> "BELGIUM"
        else -> "FRANCE"
      }
}