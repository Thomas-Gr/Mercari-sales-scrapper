package toritsu_server

import com.amazonaws.services.dynamodbv2.document.Item
import com.amazonaws.services.dynamodbv2.document.PrimaryKey
import com.amazonaws.services.dynamodbv2.document.Table
import com.amazonaws.services.dynamodbv2.document.spec.ScanSpec
import common.TORITSU_LINK_UPLOAD
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import kotlin.math.ceil
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody

/**
 * Create offers (on Toritsu) from the (mercari) backlog
 */
class BacklogOfferCreator {

  fun main() {
    for (item in readData()) {
      println("[${item.name}] New item imported")
      val images = downloadAndUploadAllImages(item.id)
      insertItemInDb(item, images)
      removeItemFromBacklog(item.id)
      println("[${item.name}] Item added")
    }
  }

  private fun removeItemFromBacklog(id: String) {
    dynamoDB.getTable("backlog").deleteItem(PrimaryKey("id", id))
  }

  private fun insertItemInDb(
    item: BacklogItem,
    images: List<String>
  ) {
    val endDate = LocalDateTime.from(Date().toInstant().atOffset(ZoneOffset.ofHours(8)))
      .plusWeeks(1)
      .toEpochSecond(ZoneOffset.ofHours(8))
      .toInt()
    val newPrice = toEuro(item.price)

    val table: Table = dynamoDB.getTable("special_sales")

    val dbItem: Item = Item()
      .withPrimaryKey("itemId", item.id)
      .withString("index", "1")
      .withString("name", item.name)
      .withString("price", newPrice.toString())
      .withString("weight", item.weight)
      .withString("nbCards", item.nbCards)
      .withString("dimension", item.dimension)
      .withInt("endDate", endDate)
      .withInt("amountAvailable", 1)
      .withInt("amountSold", 0)
      .withBoolean("isDirectSale", true)
      .withBoolean("isMercariItem", true)
      .withString("image", images.joinToString(","))

    table.putItem(dbItem)
  }

  private fun toEuro(priceInYen: Int) = ceil(priceInYen / 95.0)

  private fun downloadAndUploadAllImages(id: String): List<String> {
    val dataBuffer = ByteArray(1024)

    val images = mutableListOf<String>()
    for (i in 1..10) {
      val fileName = "${id}_$i"
      try {
        BufferedInputStream(URL("https://static.mercdn.net/item/detail/orig/photos/$fileName.jpg").openStream())
          .use { `in` ->
            FileOutputStream("/tmp/$fileName.jpg").use { fileOutputStream ->
              var bytesRead: Int
              while (`in`.read(dataBuffer, 0, 1024).also { bytesRead = it } != -1) {
                fileOutputStream.write(dataBuffer, 0, bytesRead)
              }
            }
          }

        images.add("$fileName.jpg")
        uploadImage("/tmp/$fileName.jpg", "$fileName.jpg")
      } catch (e: IOException) {
      }
    }

    return images
  }

  data class BacklogItem(
    val id: String,
    val name: String,
    val price: Int,
    val dimension: String,
    val nbCards: String,
    val weight: String
  )

  private fun readData(): List<BacklogItem> {
    val table: Table = dynamoDB.getTable("backlog")

    val scanSpec = ScanSpec()

    return table.scan(scanSpec)
      .map { entry ->
        BacklogItem(
          entry.get("id").toString(),
          entry.get("name").toString(),
          entry.get("price").toString().toInt(),
          entry.get("dimension").toString(),
          entry.get("nbCards").toString(),
          entry.get("weight").toString()
        )
      }
      .toList()
  }

  private fun uploadImage(imagePath: String, imageName: String) {
    val requestBody: RequestBody = MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("file_name", imageName)
      .addFormDataPart(
        "file", imageName,
        File(imagePath).asRequestBody("image/jpeg".toMediaTypeOrNull())
      )
      .build()

    val execute = client.newCall(
      Request.Builder()
        .url(TORITSU_LINK_UPLOAD)
        .post(requestBody)
        .build()
    ).execute()

    if (execute.code != 200) {
      throw IllegalStateException("Failed uploading files")
    }
    execute.close()
  }
}