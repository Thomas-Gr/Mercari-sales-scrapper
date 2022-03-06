import common.SALE_FILES
import common.SPREADSHEET_ID
import reporter.SingleSaleReporter

fun main() {
  for (saleFile in SALE_FILES) {
    SingleSaleReporter(SPREADSHEET_ID, saleFile).report()
  }
}