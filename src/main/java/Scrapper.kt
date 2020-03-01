import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import java.util.concurrent.TimeUnit.SECONDS

class Scrapper(driverPath: String) {
  private val driver: WebDriver

  init {
    System.setProperty("webdriver.chrome.driver", driverPath)
    driver = ChromeDriver()
    driver.manage().timeouts().implicitlyWait(2, SECONDS)
  }

  fun scrap(pageRange: IntRange, page: String): List<MercariData> {
    return pageRange
        .map { readListingPage(it, page) }
        .flatMap { it.toList() }
        .map { readOrderPage(it.first, it.second) }
  }

  private fun readListingPage(i: Int, page: String): List<Pair<String, Boolean>> {
    driver.get("https://www.mercari.com/jp/mypage/$page/?page=$i")

    return driver.findElements(By.className("mypage-item-link"))
        .map {
          val isDone = it.findElements(By.className("done")).isNotEmpty()

          Pair(
              it.getAttribute("href"),
              isDone
          )
        }
  }

  private fun readOrderPage(href: String, state: Boolean): MercariData {
    Thread.sleep(200L)
    driver.get(href)

    val elements = driver.findElements(By.cssSelector(".transact-info-table-cell"))
    val link = elements[1].findElement(By.cssSelector("a")).getAttribute("href")
    val date = elements[5].findElement(By.cssSelector("li")).text
    val description = driver.findElement(By.cssSelector(".transact-info-table-cell div")).text
    val imageSrc = driver.findElement(By.cssSelector(".transact-info-table-cell img"))
        .getAttribute("data-src")

    val split = description.split("\n")

    return MercariData(link, imageSrc, split[0], split[1].replace("[^\\d]".toRegex(), ""), date, state)
  }

  fun setUpAndWaitForConnection() {
    driver.get("https://www.mercari.com/jp/login")

    while (driver.currentUrl != "https://www.mercari.com/jp/mypage/") {
      Thread.sleep(3_000L)
    }
  }

  fun close() {
    driver.close()
  }
}
