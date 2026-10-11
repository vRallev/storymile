package software.ralf.storymile.theme

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import software.ralf.storymile.theme.generated.resources.Res
import software.ralf.storymile.theme.generated.resources.storymile_logo

/** Decorative Storymile logo that follows the active theme. */
@Composable
fun StorymileLogo(modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(Res.drawable.storymile_logo),
    contentDescription = null,
    modifier = modifier,
  )
}
