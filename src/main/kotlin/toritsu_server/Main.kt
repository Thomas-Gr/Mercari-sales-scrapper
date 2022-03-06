package toritsu_server

import com.amazonaws.regions.Regions
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.document.DynamoDB
import common.DRIVER
import common.FB_APP_ID
import common.TORITSU_SALES_ID
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import okhttp3.OkHttpClient

val client = createHttpClient()
val dynamoDbClient: AmazonDynamoDB = AmazonDynamoDBClientBuilder.standard()
  .withRegion(Regions.EU_WEST_3)
  .build()
val dynamoDB = DynamoDB(dynamoDbClient)
const val photoUploadUrl = "https://graph.facebook.com/$FB_APP_ID/photos"
const val postFeedUrl = "https://graph.facebook.com/$FB_APP_ID/feed"

fun main(args: Array<String>) {
  val driver = if (args.isEmpty()) {
    DRIVER
  } else {
    args[0]
  }
  println("Driver: $driver")

  val scrapper = Scrapper(driver)
  val backlogOfferCreator = BacklogOfferCreator()
  val facebookNewsFeeder = FacebookNewsFeeder()
  val mercariOfferTerminator = MercariOfferTerminator(scrapper)
  val itemsUpdater = ItemsUpdater(TORITSU_SALES_ID)

  scrapper.setUpWithoutConnection()

  while (true) {
    val unixTime = System.currentTimeMillis() / 1000L
    println("[$unixTime] New run")
    itemsUpdater.update()
    backlogOfferCreator.main()
    mercariOfferTerminator.main()
    facebookNewsFeeder.main()

    // Repeat every hour or so
    Thread.sleep(1000 * 60 * (50 + Random.nextLong(0, 20)))
  }
}

private fun createHttpClient(): OkHttpClient {
  val clientBuilder = OkHttpClient.Builder()

  clientBuilder.connectTimeout(10, TimeUnit.SECONDS)
  clientBuilder.writeTimeout(10, TimeUnit.SECONDS)
  clientBuilder.readTimeout(30, TimeUnit.SECONDS)

  return clientBuilder.build()
}