val scrapper = Scrapper("path")
val exporter = Exporter("path")
val sheetsExporter = SheetsExporter("sheetId")

fun main() {
  scrapper.setUpAndWaitForConnection()

  val purchasedData = scrapper.scrap(1..1, "purchased")
  val purchaseData = scrapper.scrap(1..1, "purchase")

  exporter.toFile(purchaseData.union(purchasedData))
  sheetsExporter.toSheet(purchaseData.union(purchasedData))

  scrapper.close()
}