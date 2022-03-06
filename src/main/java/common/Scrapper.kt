package common

import java.io.File
import java.util.concurrent.TimeUnit.SECONDS
import javax.imageio.ImageIO
import kotlin.random.Random
import org.openqa.selenium.By
import org.openqa.selenium.JavascriptExecutor
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import ru.yandex.qatools.ashot.AShot
import ru.yandex.qatools.ashot.coordinates.Coords
import ru.yandex.qatools.ashot.coordinates.CoordsProvider

class Scrapper(driverPath: String) {
  private val driver: WebDriver

  init {
    System.setProperty("webdriver.chrome.driver", driverPath)
    driver = ChromeDriver()
    driver.manage().timeouts().implicitlyWait(2, SECONDS)
  }

  fun scrap(pageRange: IntRange, page: String, takeScreenshot: Boolean = false): List<MercariData> {
    driver.get("https://jp.mercari.com/mypage/$page")
    Thread.sleep(2000)
    try {
      pageRange.forEach { _ ->
        driver.findElement(By.cssSelector("mer-tab-panel mer-button button")).click()
        (driver as JavascriptExecutor).executeScript("window.scrollTo(0, document.body.scrollHeight)");
        Thread.sleep(2000 + Random(42).nextLong(0, 500))
      }
    } catch (e: Exception) {}

    return readListingPage(page).map { readOrderPage(it.first, it.second, takeScreenshot) }
  }

  private fun readListingPage(page: String): List<Pair<String, Boolean>> {
    // TODO: Pagination
    return driver.findElements(By.cssSelector("mer-list-item > a"))
        .filter { it.getAttribute("href").startsWith("https://jp.mercari.com/transaction") }
        .map {
          Pair(
              it.getAttribute("href"),
              page != "purchases"
          )
        }
  }

  private fun readOrderPage(href: String, state: Boolean, takeScreenshot: Boolean): MercariData {
    Thread.sleep(200L)
    driver.get(href)
    Thread.sleep(2000L)

    var done = false
    var attempt = 0
    while (!done && attempt++ < 5) {
      try {
        val jse = driver as JavascriptExecutor
        val titleElement = jse.executeScript("return document.querySelector('main mer-list').querySelector('mer-list-item mer-item-object').shadowRoot.querySelector('.item-title')") as WebElement
        val title = titleElement.text

        val imageElement = jse.executeScript("return document.querySelector('main mer-list').querySelector('mer-list-item mer-item-object').shadowRoot.querySelector('mer-item-thumbnail').shadowRoot.querySelector('img')") as WebElement
        val imageSrc = imageElement.getAttribute("src")

        val priceElement = jse.executeScript("return document.querySelector('mer-price').shadowRoot.querySelector('.number')") as WebElement
        val price = priceElement.text.replace(",", "")

        val linkElement = jse.executeScript("return document.querySelector('main mer-list').querySelector('mer-list-item a')") as WebElement
        val link = linkElement.getAttribute("href")

        val saleInfoSpans = driver.findElements(By.cssSelector("mer-display-row span"))
        val date = saleInfoSpans[5].text

        return if (takeScreenshot) {
          val screenshot = AShot().coordsProvider(MyProvider())
            .takeScreenshot(driver, driver.findElement(By.cssSelector("#transaction-sidebar .sticky-inner-wrapper")))

          val fileName = "/tmp/%s.png".format(System.currentTimeMillis().toString())
          ImageIO.write(screenshot.image, "png", File(fileName))

          MercariData(link, imageSrc, title, price, date, state, fileName, link.substringAfterLast("/"))
        } else {
          MercariData(link, imageSrc, title, price, date, state)
        }
      } catch (e: Exception) {
        println("retry")
        e.printStackTrace()
        Thread.sleep(2500)
      }
    }

    throw IllegalStateException("")
  }

  fun setUpAndWaitForConnection() {
    driver.get("https://jp.mercari.com/signin?params=client_id%3DbP4zN6jIZQeutikiUFpbx307DVK1pmoW%26code_challenge%3DVXBlXU5jH_l3iJkNUtGgUmftajA84eO3Te65VrrgPq4%26code_challenge_method%3DS256%26nonce%3D6L9b1nBJteoG%26redirect_uri%3Dhttps%253A%252F%252Fjp.mercari.com%252Fauth%252Fcallback%26response_type%3Dcode%26scope%3Dmercari%2520openid%26state%3DeyJwYXRoIjoiLyIsInJhbmRvbSI6ImRRbmhteC1ZVnlYOCJ9%26target%3D%252Fsignin")

    while (driver.currentUrl != "https://jp.mercari.com/") {
      Thread.sleep(3_000L)
    }
  }

  fun close() {
    driver.close()
  }

  class MyProvider : CoordsProvider() {
    override fun ofElement(driver: WebDriver, element: WebElement): Coords {
      val point = element.location
      val dimension = element.size
      return Coords(
        point.getX() - 10,
        point.getY() - 10,
        dimension.getWidth() + 20,
        dimension.getHeight() + 20
      )
    }
  }
}
