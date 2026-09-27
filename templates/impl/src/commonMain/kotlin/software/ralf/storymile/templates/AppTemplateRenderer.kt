package software.ralf.storymile.templates

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.isSpecified
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.presenter.compose.backgesture.BackGestureDispatcherPresenter
import software.ralf.app.platform.presenter.compose.backgesture.ForwardBackPressEventsToPresenters
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.screen.DefaultScreenSizeProvider
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.StorymileTheme

/**
 * Compose renderer for the application's outer template layer.
 *
 * It owns theme installation, safe-area padding, window-size reporting, renderer delegation, and
 * forwarding platform back events to active presenters.
 */
@Inject
@ContributesRenderer
class AppTemplateRenderer(
  private val backGestureDispatcherPresenter: BackGestureDispatcherPresenter,
  private val screenSizeProvider: DefaultScreenSizeProvider,
) : ComposeRenderer<AppTemplate>() {
  @Composable
  override fun Compose(model: AppTemplate, modifier: Modifier) {
    val screenSize by screenSizeProvider.screenSize.collectAsState()
    ReportScreenSize()

    CompositionLocalProvider(LocalScreenSize provides screenSize) {
      StorymileTheme {
        Surface(
          modifier = modifier.fillMaxSize(),
          color = AppTheme.colorScheme.background,
          contentColor = AppTheme.colorScheme.onBackground,
        ) {
          SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
              when (model) {
                is AppTemplate.AdaptiveTemplate -> AdaptiveTemplateContent(model)
              }
              backGestureDispatcherPresenter.ForwardBackPressEventsToPresenters()
            }
          }
        }
      }
    }
  }

  @Composable
  private fun ReportScreenSize() {
    val windowInfo = LocalWindowInfo.current
    LaunchedEffect(windowInfo) {
      snapshotFlow { windowInfo.containerDpSize }
        .filter { it.isSpecified }
        .map { ScreenSize.from(width = it.width, height = it.height) }
        .distinctUntilChanged()
        .collect { screenSizeProvider.update(it) }
    }
  }

  @Composable
  private fun AdaptiveTemplateContent(template: AppTemplate.AdaptiveTemplate) {
    BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      val tabPlacement =
        when (ScreenSize.from(maxWidth, maxHeight).category) {
          ScreenSize.Category.PHONE -> TabPlacement.BOTTOM
          ScreenSize.Category.TABLET -> TabPlacement.START
        }

      AppShell(template, tabPlacement)
      template.overlay?.let { Render(it, Modifier.matchParentSize()) }
    }
  }

  @Composable
  private fun AppShell(
    template: AppTemplate.AdaptiveTemplate,
    tabPlacement: TabPlacement,
  ) {
    // Keep each slot in the same composition position when tabs move between bar and rail.
    Layout(
      modifier = Modifier.fillMaxSize(),
      content = {
        Box(propagateMinConstraints = true) { Render(template.content, Modifier.fillMaxSize()) }
        Box(propagateMinConstraints = true) {
          CompositionLocalProvider(LocalTabPlacement provides tabPlacement) {
            template.tabs?.let { Render(it) }
          }
        }
        Box(propagateMinConstraints = true) { template.playback?.let { Render(it) } }
      },
    ) { measurables, constraints ->
      val width = constraints.maxWidth
      val height = constraints.maxHeight
      val playback =
        measurables[2].measure(
          if (template.playback == null) Constraints.fixed(0, 0)
          else Constraints(minWidth = width, maxWidth = width, maxHeight = height)
        )
      val remainingHeight = height - playback.height
      val tabsAtStart = tabPlacement == TabPlacement.START
      val tabs =
        measurables[1].measure(
          when {
            template.tabs == null -> Constraints.fixed(0, 0)
            tabsAtStart ->
              Constraints(
                maxWidth = width,
                minHeight = remainingHeight,
                maxHeight = remainingHeight,
              )
            else -> Constraints(minWidth = width, maxWidth = width, maxHeight = remainingHeight)
          }
        )
      val contentWidth = width - if (tabsAtStart) tabs.width else 0
      val contentHeight = remainingHeight - if (tabsAtStart) 0 else tabs.height
      val content = measurables[0].measure(Constraints.fixed(contentWidth, contentHeight))

      layout(width, height) {
        content.placeRelative(if (tabsAtStart) tabs.width else 0, 0)
        tabs.placeRelative(0, if (tabsAtStart) 0 else height - tabs.height)
        playback.placeRelative(0, contentHeight)
      }
    }
  }
}
