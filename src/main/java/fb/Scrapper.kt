package fb

import common.*
import fb.Helper.generateMessage
import fb.Helper.getValuesAlreadyPresentInSpreadsheet
import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.WebDriver
import org.openqa.selenium.chrome.ChromeDriver
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.io.File
import java.lang.Thread.sleep
import java.util.concurrent.TimeUnit.SECONDS

private val NEW_LINE = Keys.chord(Keys.LEFT_SHIFT, Keys.RETURN)
private const val START = 2

fun main() {
  val scrapper = Scrapper(DRIVER, SPREADSHEET_FB)

  scrapper.scrap()
}

class Scrapper(driverPath: String, private val spreadsheetId: String) {
  private val driver: WebDriver
  private val robot = Robot()

  init {
    System.setProperty("webdriver.chrome.driver", driverPath)
    driver = ChromeDriver()
    driver.manage().timeouts().implicitlyWait(2, SECONDS)

    robot.autoDelay = 40
    robot.isAutoWaitForIdle = true
  }

  fun scrap() {
    val items = getValuesAlreadyPresentInSpreadsheet(spreadsheetId)

    setUpAndWaitForConnection()

    sleep(15000L)
    var i = 0
    for (item in items) {
      i++

      if (START > i) {
        continue
      }
      val id = item[0]
      val userFacingId = item[6]

      println("Item $userFacingId: START")

      val uploadImage = driver.findElements(By.cssSelector("form li:nth-child(2)")).last()
      val textSpan = driver.findElements(By.cssSelector("div[data-contents]")).last()

      uploadImage.click()

      val folderName = Helper.hashFileName((id as String).toInt())
      val folder = FB_SALES_OUTPUT_FOLDER + "/$folderName/"
      val files = File(folder).walk().sortedBy { it.name }.filter { it.isFile }.toList()

      copyToClipboard(files[0].absolutePath)
      sleep(3000L)
      leftClick()
      selectPath()
      paste()
      enter()
      sleep(1000L)
      enter()
      sleep(500L)
      textSpan.click()

      generateMessage(item, folderName, files).split("\n").forEach {
        textSpan.sendKeys(it)
        textSpan.sendKeys(NEW_LINE)
      }

      println("Item $userFacingId: message written")
      waitForImageUploaded()
      println("Item $userFacingId: picture uploaded")
      sleep(500L)

      while (textSpan.text.isNotEmpty()) {
        textSpan.sendKeys(Keys.ENTER)
        sleep(3000L)
      }

      println("Item $userFacingId: DONE\n")
      sleep(10000L)
    }

    close()
  }

  private fun copyToClipboard(s: String) {
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(s), null)
  }

  private fun enter() {
    robot.delay(50)
    robot.keyPress(KeyEvent.VK_ENTER)
    robot.keyRelease(KeyEvent.VK_ENTER)
  }

  private fun paste() {
    robot.delay(50)
    robot.keyPress(KeyEvent.VK_META)
    robot.keyPress(KeyEvent.VK_V)
    robot.keyRelease(KeyEvent.VK_META)
    robot.keyRelease(KeyEvent.VK_V)
  }

  private fun selectPath() {
    robot.delay(50)
    robot.keyPress(KeyEvent.VK_META)
    robot.keyPress(KeyEvent.VK_SHIFT)
    robot.keyPress(KeyEvent.VK_G)
    robot.keyRelease(KeyEvent.VK_META)
    robot.keyRelease(KeyEvent.VK_SHIFT)
    robot.keyRelease(KeyEvent.VK_G)
  }

  private fun leftClick() {
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
    robot.delay(200)
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
    robot.delay(200)
  }

  private fun setUpAndWaitForConnection() {
    driver.get(FB_PATH_ENTRY)

    while (driver.currentUrl != FB_PATH_ENTRY) {
      sleep(3_000L)
    }

    sleep(3_000L)
    driver.get(FB_PATH_GROUP)
    sleep(3_000L)
  }

  private fun waitForImageUploaded() {
    sleep(3_000L)
    var i = 0
    while (photoNotUploaded()) {
      println("Try " + ++i)
      sleep(1_000L)
    }
  }

  private fun photoNotUploaded(): Boolean {
    return try {
      val findElement = driver.findElement(By.cssSelector("div[role=progressbar]"))
      findElement == null
    } catch (e: Exception) {
      false
    }
  }

  private fun close() {
    driver.close()
  }
}
