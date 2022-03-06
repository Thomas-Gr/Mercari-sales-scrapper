package japanpost

import common.DRIVER

val dataReader = DynamoDbDataReader()
val scrapper = Scrapper(DRIVER)

fun main() {
  val data = dataReader.readData()

  scrapper.setUpAndWaitForConnection()

  val links = scrapper.scrap(data)

  println(links.joinToString(separator = "\n") { "<a href=\"$it\">$it</a><br/>" })

  scrapper.close()
}