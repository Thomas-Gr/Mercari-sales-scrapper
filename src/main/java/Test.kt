fun main() {
  val mercariData = MercariData("1", "2", "3", "4", "5", "6")
  val mercariData2 = MercariData("12", "2", "3", "42", "5", "62")
  val mercariData3 = MercariData("13", "2", "3", "43", "5", "63")

  val sheetsExporter = SheetsExporter("1iZvBQ0qfEKCDTT86DXVftPgk9gakGkoCziR9oxS9YKo")

  sheetsExporter.toSheet(listOf(mercariData, mercariData2, mercariData3))
}