fun main() {
  val saleFiles = listOf(
      "1DJbhn_niubdvIR-6hKF4g5C_NyqTrjgduR5qZqEXkVQ", // Marta
      "1qKh5GqpNxVPO2z_M7AytpDj74nX09ZxG2sJytsex8Ys", // Sabrina
      "1rwMGZZmj4al6A4irBBdGbCUmGqdbXHDipvODvGNHHP8" // Maxime
  )

  for (saleFile in saleFiles) {
    SingleSaleReporter("1iZvBQ0qfEKCDTT86DXVftPgk9gakGkoCziR9oxS9YKo", saleFile).report()
  }
}