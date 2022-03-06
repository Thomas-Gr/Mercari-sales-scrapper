package common

data class MercariData(
    val link: String,
    val image: String,
    val title: String,
    val price: String,
    val date: String,
    val isDone: Boolean,
    val screenshotPath: String = "",
    val id: String = "")
