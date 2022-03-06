package toritsu_server

import java.util.concurrent.TimeUnit.SECONDS
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver

class Scrapper(driverPath: String) {
  private val driver: WebDriver

  init {
    System.setProperty("webdriver.chrome.driver", driverPath)
    driver = ChromeDriver()
    driver.manage().timeouts().implicitlyWait(2, SECONDS)
  }

  fun isStillAvailable(id: String): Boolean {
    Thread.sleep(200L)
    driver.get("https://jp.mercari.com/item/$id")
    Thread.sleep(2000L)

    var attempt = 0
    while (attempt++ < 3) {
      try {
        val map = driver.findElements(By.cssSelector("mer-text")).map { it.text }

        return !map.any { it.contains("売り切れのためコメントできません") }
      } catch (e: Exception) {
        println("retry")
        e.printStackTrace()
        Thread.sleep(2500)
      }
    }

    return false
  }

  fun setUpWithoutConnection() {
    driver.get("https://jp.mercari.com/")

    Thread.sleep(5_000L)
  }

  fun close() {
    driver.close()
  }
}
