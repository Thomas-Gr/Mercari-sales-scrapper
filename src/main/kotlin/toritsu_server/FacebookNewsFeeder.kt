package toritsu_server

import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec
import com.amazonaws.services.dynamodbv2.document.utils.ValueMap
import com.amazonaws.services.dynamodbv2.model.ReturnValue
import common.FB_TOKEN
import common.S3_BUCKET_PICTURES
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.stream.Collectors

/**
 * Create posts on Facebook for new products.
 */
class FacebookNewsFeeder {
  fun main() {
    val nonPublishedLiveItems = getNonPublishedLiveItems()

    if (nonPublishedLiveItems.isEmpty()) {
      return
    }
    val images = nonPublishedLiveItems.map { it.image }.map { uploadPhotoToFacebook(it) }.toList()
    postToFacebook(nonPublishedLiveItems, images)
    nonPublishedLiveItems.forEach { markItemAsPublished(it) }
    println("New post published on Facebook!")
  }

  private fun markItemAsPublished(item: LiveItem) {
    val updateItemSpec = UpdateItemSpec()
      .withPrimaryKey("itemId", item.id, "index", "1")
      .withUpdateExpression("set isPublished = :isPublished")
      .withValueMap(ValueMap().withBoolean(":isPublished", true))
      .withReturnValues(ReturnValue.UPDATED_NEW)

    dynamoDB.getTable("special_sales").updateItem(updateItemSpec)
  }

  private data class LiveItem(val id: String, val name: String, val price: Double, val image: String)

  private fun getNonPublishedLiveItems(): List<LiveItem> {
    val table: Table = dynamoDB.getTable("special_sales")

    val nameMap = mapOf(Pair("#endDate", "endDate"))
    val valueMap = mapOf(Pair(":endDate", Date().time / 1000))
    val scanSpec = ScanSpec()
      .withFilterExpression("#endDate > :endDate and attribute_not_exists(isPublished)")
      .withNameMap(nameMap)
      .withValueMap(valueMap)

    return table.scan(scanSpec)
      .map {
        LiveItem(
          it.get("itemId").toString(),
          it.get("name").toString(),
          it.get("price").toString().replace(",", ".").toDouble(),
          S3_BUCKET_PICTURES + it.get("image")
            .toString().split(",")[0].trim()
        )
      }
      .sortedByDescending { it.price }
      .toList()
  }

  private fun uploadPhotoToFacebook(image: String): String {
    val conn: HttpURLConnection = URL(photoUploadUrl).openConnection() as HttpURLConnection
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.requestMethod = "POST"

    val data = """{"url":"$image", "published": false, "access_token":"$FB_TOKEN"}"""
    val out = OutputStreamWriter(conn.outputStream)
    out.write(data)
    out.close()

    conn.inputStream.use {
      return BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8))
        .lines()
        .collect(Collectors.joining("\n"))
        .split("\"")[3]
    }
  }

  private fun postToFacebook(items: List<LiveItem>, images: List<String>) {
    val conn: HttpURLConnection = URL(postFeedUrl).openConnection() as HttpURLConnection
    conn.setRequestProperty("Content-Type", "application/json")
    conn.doOutput = true
    conn.requestMethod = "POST"

    val media = images.joinToString(", ") { """{"media_fbid": "$it"}""" }

    val data = if (items.size == 1) {
      val item = items[0]
      val message = """[VENTE]\n\n⏰ Nouvel objet en vente sur Toritsu ⏰\n\n- Nom: ${item.name}\n- Prix: ${toReadablePrice(item)} €\n- Lien: https://toritsu-sales.com/item_${item.id}\n- Envoi depuis le Japon (donc frais de port et frais de douane à prévoir)\n\nA bientôt !""".trimIndent()

      """{"message":"$message", "attached_media": [$media], "access_token":"$FB_TOKEN"}"""
    } else {
      val itemsList =
        items.joinToString("\\n") { " - ${it.name} (${toReadablePrice(it)} €)" }
      val message = """[VENTE]\n\n⏰ Nouveaux objets en vente sur Toritsu ⏰\n\n$itemsList\n\n➡️➡️ https://www.toritsu-sales.com ⬅️⬅️\n\nEnvois depuis le Japon (donc frais de port et frais de douane à prévoir)\n\nA bientôt !""".trimIndent()
      val media = images.joinToString(", ") { """{"media_fbid": "$it"}""" }

      """{"message":"$message", "attached_media": [$media], "access_token":"$FB_TOKEN"}"""
    }

    println(data)
    val out = OutputStreamWriter(conn.outputStream)
    out.write(data)
    out.close()

    conn.inputStream.use {
      println(
        BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8))
          .lines()
          .collect(Collectors.joining("\n"))
      )
    }
  }

  private fun toReadablePrice(it: LiveItem) =
    "%.2f".format(it.price).replace(".", ",")
}