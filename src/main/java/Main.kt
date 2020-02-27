val scrapper = Scrapper("path")
val exporter = Exporter("path")

fun main() {
  scrapper.setUpAndWaitForConnection()

  val purchasedData = scrapper.scrap(1..1, "purchased")
  val purchaseData = scrapper.scrap(1..1, "purchase")

  exporter.toFile(purchaseData.union(purchasedData))

  scrapper.close()
}