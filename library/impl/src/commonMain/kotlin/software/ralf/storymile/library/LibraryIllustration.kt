package software.ralf.storymile.library

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import software.ralf.storymile.library.impl.generated.resources.Res
import software.ralf.storymile.library.impl.generated.resources.library_illustration

@Composable
internal fun LibraryIllustration(modifier: Modifier = Modifier) {
  Image(
    painter = painterResource(Res.drawable.library_illustration),
    contentDescription = null,
    modifier = modifier,
  )
}
