package software.ralf.storymile.playback

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.playback.impl.generated.resources.Res
import software.ralf.storymile.playback.impl.generated.resources.back_fifteen
import software.ralf.storymile.playback.impl.generated.resources.replay_fifteen

@Composable
internal fun ReplayFifteen(modifier: Modifier = Modifier) {
  Icon(
    painterResource(Res.drawable.replay_fifteen),
    contentDescription = stringResource(Res.string.back_fifteen),
    modifier = modifier,
  )
}
