import common.SALE_FILES
import common.SPREADSHEET_ID

fun main() {
  for (saleFile in SALE_FILES) {
    SingleSaleReporter(SPREADSHEET_ID, saleFile).report()
  }
}