package fb

import common.FB_SALES_MERGER_FOLDER
import common.FB_SALES_OUTPUT_FOLDER
import common.FB_SALES_SHEET_ID
import fb.Helper.hashFileName
import java.awt.Image.SCALE_SMOOTH
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.awt.image.BufferedImage.TYPE_INT_RGB
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.*


fun main() {
  val scrapper = PictureMerger(
      FB_SALES_SHEET_ID,
      FB_SALES_OUTPUT_FOLDER,
      FB_SALES_MERGER_FOLDER)

  scrapper.merge()
}

class PictureMerger(private val spreadsheetId: String, private val inputFolder: String, private val outputFolder: String) {

  fun merge() {
    val spreadsheetData = Helper.getValuesAlreadyPresentInSpreadsheet(spreadsheetId)

    val files = File(inputFolder)
        .walk()
        .filter { it.isFile }
        .filter { !it.isHidden }
        .filter { it.name.contains("jpg") }
        .map { it.path to it }
        .toMap()

    spreadsheetData.forEach {
      val front = files["%s/%s/0.jpg".format(inputFolder, hashFileName(it[0].toString().toInt()))]
      val back = files["%s/%s/1.jpg".format(inputFolder, hashFileName(it[0].toString().toInt()))]

      if (front != null) {
        val frontPicture = rotate(ImageIO.read(front))
        val backPicture = rotate(ImageIO.read(back))

        val concatImage = BufferedImage(
            frontPicture.width + backPicture.width,
            max(frontPicture.height, backPicture.height),
            TYPE_INT_RGB)

        val g2d = concatImage.createGraphics()
        g2d.drawImage(frontPicture, 0, 0, null)
        g2d.drawImage(backPicture, frontPicture.width, 0, null)
        g2d.dispose()

        val result = concatImage.getScaledInstance(concatImage.width / 2, concatImage.height / 2, SCALE_SMOOTH)

        val buffered = BufferedImage(concatImage.width / 2, concatImage.height / 2, TYPE_INT_RGB)
        buffered.graphics.drawImage(result, 0, 0, null)

        try {
          ImageIO.write(buffered, "jpg", File("%s/%s.jpg".format(outputFolder, it[0])))
        } catch (e: Exception) {}
      }
    }
  }

  private fun rotate(frontPicture: BufferedImage): BufferedImage {
    val rads = Math.toRadians(90.0)
    val sin = abs(sin(rads))
    val cos = abs(cos(rads))
    val w = floor(frontPicture.width * cos + frontPicture.height * sin).toInt()
    val h = floor(frontPicture.height * cos + frontPicture.width * sin).toInt()
    val rotatedImage = BufferedImage(w, h, frontPicture.type)
    val at = AffineTransform()
    at.translate((w / 2).toDouble(), (h / 2).toDouble())
    at.rotate(rads, 0.0, 0.0)
    at.translate(-frontPicture.width.toDouble() / 2, -frontPicture.height.toDouble() / 2)
    val rotateOp = AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR)
    rotateOp.filter(frontPicture, rotatedImage)

    return rotatedImage
  }
}
