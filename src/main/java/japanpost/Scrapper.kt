package japanpost

import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.support.ui.Select
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit.SECONDS

import org.openqa.selenium.remote.CapabilityType

import org.openqa.selenium.remote.DesiredCapabilities
import org.openqa.selenium.Alert
import org.openqa.selenium.chrome.ChromeOptions
import java.awt.Desktop
import java.lang.Exception
import java.lang.IllegalStateException
import java.net.URI
import java.util.ArrayList

class Scrapper(driverPath: String) {
  private val driver: WebDriver
  private val jsExecutorDriver: JavascriptExecutor

  init {
    System.setProperty("webdriver.chrome.driver", driverPath)
    val chromeOptions = ChromeOptions()
    chromeOptions
        .setCapability(CapabilityType.UNEXPECTED_ALERT_BEHAVIOUR, UnexpectedAlertBehaviour.IGNORE)

    driver = ChromeDriver(chromeOptions)
    driver.manage().timeouts().implicitlyWait(2, SECONDS)
    jsExecutorDriver = driver
  }

  fun scrap(userData: List<Data>): List<String> {
    return userData.map { createFile(it) }
  }

  private fun createFile(userData: Data): String {
    val createLabelLink = driver.findElement(By.cssSelector(".listmark-none > li > a"))
    createLabelLink.click()
    sleep(100L)

    jsExecutorDriver.executeScript("regist()")

    findUser(userData)

    val buttons = driver.findElements(By.cssSelector("input[type=button]"))
    buttons[buttons.size - 2].click()

    try {
      waitForPage("https://www.int-mypage.post.japanpost.jp/mypage/M060400.do")
    } catch (f: UnhandledAlertException) {
      driver.switchTo().alert().accept()
    }

    sleep(300L)

    driver.findElement(By.id("M060800_shippingBean_sendType4")).click()

    userData.items.forEach {
      val contentName = driver.findElement(By.id("M060800_itemBean_pkg"))
      contentName.click()
      contentName.sendKeys(it.name)

      val cost = driver.findElement(By.id("M060800_itemBean_cost_value"))
      cost.click()
      cost.sendKeys(it.unitPrice.toString())

      val quantity = driver.findElement(By.id("M060800_itemBean_num_value"))
      quantity.click()
      quantity.sendKeys(it.quantity.toString())

      jsExecutorDriver.executeScript("submitCommand('itemAdd2')")

      try {
        driver.findElement(By.id("warningMsgOff")).click()
        driver.findElements(By.cssSelector("button[type=button]"))[1].click()
      }  catch (e: Exception) {
        // In case it doesn't appear anymore. Not clean, but it's ok for now...
      }

      sleep(300L)
    }
    sleep(200L)
    Select(driver.findElement(By.id("M060800_shippingBean_pkgType"))).selectByVisibleText("Sale of goods")
    driver.findElement(By.id("M060800_ShippingBean_danger")).click()
    jsExecutorDriver.executeScript("inforegist()")

    waitForPage("https://www.int-mypage.post.japanpost.jp/mypage/M060800.do")

    driver.findElement(By.id("M060900_shippingBean_totalWeight_value"))
        .sendKeys(userData.totalWeight.toString())
//    driver.findElement(By.id("M060900_shippingBean_noCmtrue"))
//        .click()
    jsExecutorDriver.executeScript("check()")

    waitForPage("https://www.int-mypage.post.japanpost.jp/mypage/M060900.do")

    jsExecutorDriver.executeScript("submitRegist()")
    waitForPage("https://www.int-mypage.post.japanpost.jp/mypage/M061000.do")
    jsExecutorDriver.executeScript("submitCommand('print')")

    waitForPage("https://www.int-mypage.post.japanpost.jp/mypage/M061100.do")

    val link = driver.findElement(By.cssSelector("iframe"))
        .getAttribute("src")

    println(link)

    sleep(1000L)

    jsExecutorDriver.executeScript("submitCommand('regist')")

    return link
  }

  private fun findUser(userData: Data) {
    val numberSearch = Select(driver.findElement(By.id("M060400_addrSearchBean_maxRec_value")))
    numberSearch.selectByIndex(6)
    sleep(2000L)
    val recipients = driver.findElements(By.cssSelector(".mrgT10 > table"))[1]
    val allRecipients = recipients.findElements(By.cssSelector("tr"))
    val text = recipients.text

    text.split("\n").forEachIndexed { index, value ->
      run {
        if (value.trim().startsWith(userData.name)) {
          val line = allRecipients[index]
          println(line.text)
          line.findElement(By.cssSelector("input[type=radio]")).click()

          return
        }
      }
    }

    // The user was not found, create it
    createUser(userData)
  }

  private fun createUser(userData: Data) {
    if (userData.address == null) {
      throw IllegalStateException("No address defined for user " + userData.name)
    }
    jsExecutorDriver.executeScript("submitCommand('add')")
    driver.findElement(By.id("M060500_addrToBean_nam")).sendKeys(userData.name)
    Select(driver.findElement(By.id("M060500_addrToBean_couCode"))).selectByVisibleText(userData.address.country)
    driver.findElement(By.id("M060500_addrToBean_add2")).sendKeys(userData.address.street)
    driver.findElement(By.id("M060500_addrToBean_add3")).sendKeys(userData.address.city)
    driver.findElement(By.id("M060500_addrToBean_postal")).sendKeys(userData.address.postalCode)

    jsExecutorDriver.executeScript("submitCommand('regist')")
    sleep(1000L)
    jsExecutorDriver.executeScript("submitCommand('update')")
    println("Address added for " + userData.name)
    sleep(500L)

    findUser(userData)
  }

  fun setUpAndWaitForConnection() {
    driver.get("https://www.int-mypage.post.japanpost.jp/mypage/M010000.do?request_locale=en")
    waitForPage("https://www.int-mypage.post.japanpost.jp/mypage/M010000.do")
  }

  private fun waitForPage(link: String) {
    while (driver.currentUrl != link) {
      sleep(300L)
    }
  }

  fun close() {
    driver.close()
  }
}
