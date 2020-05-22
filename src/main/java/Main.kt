import common.DRIVER
import common.LOCAL_EXPORT
import common.SPREADSHEET_ID

val scrapper = Scrapper(DRIVER)
val exporter = Exporter(LOCAL_EXPORT)
val sheetsExporter = SheetsExporter(SPREADSHEET_ID, sort = true)

fun main() {
  scrapper.setUpAndWaitForConnection()

  val purchasedData = scrapper.scrap(1..5, "purchased")
  val purchaseData = scrapper.scrap(1..20, "purchase")

  exporter.toFile(purchaseData.union(purchasedData))
  sheetsExporter.toSheet(purchaseData.union(purchasedData))

  scrapper.close()
}