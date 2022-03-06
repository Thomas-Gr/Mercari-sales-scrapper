package japanpost

data class Address(
    val country: String,
    val street: String,
    val city: String,
    val postalCode: String)

data class Item(
    val name: String,
    val unitPrice: Int,
    val quantity: Int)

data class Data(
    val name: String,
    val items: List<Item>,
    val totalWeight: Int,
    val address: Address?)