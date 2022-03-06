import common.DRIVER
import common.LOCAL_EXPORT
import common.SPREADSHEET_ID
import common.Scrapper
import scanner.Exporter
import scanner.SheetsExporter

val scrapper = Scrapper(DRIVER)
val exporter = Exporter(LOCAL_EXPORT)
val sheetsExporter = SheetsExporter(SPREADSHEET_ID, sort = true)

fun main() {
  scrapper.setUpAndWaitForConnection()

  val itemsInProgress = scrapper.scrap(0..0, "purchases")
  val itemsDone = scrapper.scrap(0..1, "purchases/completed")

  exporter.toFile(itemsInProgress.union(itemsDone))
  for (i in 1..5) {
    try {
      sheetsExporter.toSheet(itemsInProgress.union(itemsDone))
      break
    } catch (e: Exception) {
      e.printStackTrace()
      Thread.sleep(i * 1000L)
    }
  }

  scrapper.close()
}