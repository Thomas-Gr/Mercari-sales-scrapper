package fb

import common.FB_SALES_INPUT_FOLDER
import common.FB_SALES_OUTPUT_FOLDER
import common.FB_SALES_SHEET_ID
import fb.Helper.getValuesAlreadyPresentInSpreadsheet
import fb.Helper.hashFileName
import java.io.File


fun main() {
  PictureGrouper(FB_SALES_SHEET_ID, FB_SALES_INPUT_FOLDER, FB_SALES_OUTPUT_FOLDER)
      .run()
}

private const val START = 1

class PictureGrouper(
    private val spreadsheetId: String,
    private val inputFolder: String,
    private val outputFolder: String) {

  fun run() {
    val valuesAlreadyPresentInSpreadsheet = getValuesAlreadyPresentInSpreadsheet(spreadsheetId)

    valuesAlreadyPresentInSpreadsheet.subList(START - 1, valuesAlreadyPresentInSpreadsheet.size)
        .forEach { println(it) }

    preparePhotosAndVideos()
  }

  private fun preparePhotosAndVideos() {
    val files = File(inputFolder).walk().sortedBy { it.name }

    files.filter { it.name.contains("jpg") }
        .forEachIndexed { i, file ->
          run {
            val index = i / 2 + 1
            val num = i % 2
            val folder = hashFileName(START + index - 1)

            file.copyTo(File("$outputFolder/$folder/$num.${file.extension}"))
          }
        }

    files.filter { it.name.contains("mp4") }
        .forEachIndexed { i, file ->
          run {
            val index = 100 + i
            val folder = hashFileName(START + index - 1)
            file.copyTo(File("$outputFolder/$folder/0.${file.extension}"))
          }
        }
  }

}
