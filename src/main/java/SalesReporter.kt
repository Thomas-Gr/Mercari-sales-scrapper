fun main() {
  val saleFiles = listOf(
      "example"
  )

  for (saleFile in saleFiles) {
    SingleSaleReporter("example", saleFile).report()
  }
}