import com.google.common.base.Charsets.UTF_8
import java.io.File

class Exporter(private val exportFile: String) {
    fun toFile(data: Collection<MercariData>) {
      val text = data
          .joinToString (separator = "\n") {
            "%s;%s;%s;%s;%s;%s".format(
                it.link, it.date, it.price, it.image, it.title, if (it.isDone) "Done" else "In Progress")
          }

      File(exportFile).writeText("Link;Date;Price;Image Link;Title;State\n$text", UTF_8)
    }
}
