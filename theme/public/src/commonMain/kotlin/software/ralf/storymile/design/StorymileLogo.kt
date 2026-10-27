package software.ralf.storymile.design

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.painterResource
import software.ralf.storymile.theme.generated.resources.Res
import software.ralf.storymile.theme.generated.resources.storymile_logo

/** Hidden action for [StorymileLogo]. Defaults to no action. */
val LocalOnLogoLongPress = staticCompositionLocalOf<() -> Unit> { {} }

/** Themed logo with a hidden five-second hold action supplied by [LocalOnLogoLongPress]. */
@Composable
fun StorymileLogo(modifier: Modifier = Modifier) {
  Image(
    painter = storymileLogoPainter(),
    contentDescription = null,
    modifier = modifier.hiddenLongPress(LocalOnLogoLongPress.current),
  )
}

/** Returns the current theme's boat artwork without gesture handling. */
@Composable fun storymileLogoPainter(): Painter = painterResource(Res.drawable.storymile_logo)
