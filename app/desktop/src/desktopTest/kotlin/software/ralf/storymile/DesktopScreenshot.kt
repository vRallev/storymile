package software.ralf.storymile

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toAwtImage
import java.io.File
import javax.imageio.ImageIO

internal fun ImageBitmap.saveScreenshot(name: String) {
  val file = File("build/reports/screenshots/$name.png")
  file.parentFile.mkdirs()
  ImageIO.write(toAwtImage(), "png", file)
}
