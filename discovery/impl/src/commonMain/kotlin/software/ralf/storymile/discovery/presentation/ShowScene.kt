package software.ralf.storymile.discovery.presentation

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.podcasts_to_explore
import software.ralf.storymile.theme.AppTheme

internal data class ShowScene(
  override val entries: List<NavEntry<Int>>,
  override val previousEntries: List<NavEntry<Int>>,
  val catalog: State<ShowContentPresenter.Model>,
  val catalogState: LazyListState,
) : Scene<Int> {
  override val key: Any = entries.last().contentKey

  override val content: @Composable () -> Unit = {
    val catalog = catalog.value
    val scope = LocalNavAnimatedContentScope.current
    val episode = entries.size > 1
    val progress by
      scope.transition.animateFloat(
        transitionSpec = { tween(450) },
        label = "show-pane-progress",
      ) { state ->
        if ((state == EnterExitState.Visible) == episode) 1f else 0f
      }
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 32.dp).clipToBounds()) {
      val gutter = 24.dp
      val paneGap = gutter * 2 + 1.dp
      val paneSpace = maxWidth - paneGap
      val catalogWidth = paneSpace * (0.85f / 2.2f)
      val wideWidth = paneSpace - catalogWidth
      val podcastWidth = wideWidth + (catalogWidth - wideWidth) * progress
      val travel = catalogWidth + paneGap
      val podcastOffset = travel * (1f - progress)
      if (!episode) {
        LazyColumn(
          modifier =
            Modifier.offset { IntOffset(x = (-travel * progress).roundToPx(), y = 0) }
              .width(catalogWidth)
              .fillMaxHeight()
              .testTag("show-list"),
          state = catalogState,
          contentPadding = PaddingValues(vertical = 16.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          item {
            Text(
              text = stringResource(Res.string.podcasts_to_explore),
              modifier = Modifier.padding(bottom = 8.dp).semantics { heading() },
              style = AppTheme.typography.titleLarge,
              fontWeight = FontWeight.SemiBold,
            )
          }
          items(catalog.podcasts, key = { it.id }) { podcast ->
            PodcastRow(
              podcast = podcast,
              onClick = { catalog.onPodcastSelected(podcast) },
              selected = catalog.podcast.id == podcast.id,
            )
          }
        }
      }
      Box(Modifier.offset(x = podcastOffset).width(podcastWidth).fillMaxHeight()) {
        entries.first().Content()
      }
      if (!episode) {
        VerticalDivider(
          modifier =
            Modifier.offset(x = podcastOffset - gutter - 1.dp).testTag("show-catalog-divider"),
          color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
      }
      if (episode) {
        VerticalDivider(
          modifier =
            Modifier.offset(x = podcastOffset + podcastWidth + gutter)
              .testTag("show-episode-divider"),
          color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
        Box(
          Modifier.offset(x = podcastOffset + podcastWidth + paneGap)
            .width(wideWidth)
            .fillMaxHeight(),
        ) {
          entries.last().Content()
        }
      }
    }
  }
}
