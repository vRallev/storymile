package software.ralf.storymile.templates

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetScaffoldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import dev.zacsweers.metro.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.backgesture.BackGestureDispatcherPresenter
import software.ralf.app.platform.presenter.compose.backgesture.ForwardBackPressEventsToPresenters
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.Render
import software.ralf.storymile.screen.DefaultScreenSizeProvider
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.screen.ScreenSize
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.StorymileTheme
import software.ralf.storymile.theme.ThemeEnvironment
import software.ralf.storymile.theme.appLayerShadow

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
  private val themeEnvironment: ThemeEnvironment,
) : ComposeRenderer<AppTemplate>() {
  @Composable
  override fun Compose(model: AppTemplate, modifier: Modifier) {
    val screenSize by screenSizeProvider.screenSize.collectAsState()
    ReportScreenSize()

    CompositionLocalProvider(LocalScreenSize provides screenSize) {
      StorymileTheme(environment = themeEnvironment) {
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
    Box(Modifier.fillMaxSize()) {
      val screenSize = LocalScreenSize.current
      val tabPlacement =
        when {
          screenSize.width >= 1200.dp -> TabPlacement.START_EXPANDED
          screenSize.category == ScreenSize.Category.PHONE -> TabPlacement.BOTTOM
          else -> TabPlacement.START
        }

      AppShell(template, tabPlacement)
      template.overlay?.let {
        Box(Modifier.matchParentSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
          Render(it, Modifier.fillMaxSize())
        }
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun AppShell(
    template: AppTemplate.AdaptiveTemplate,
    tabPlacement: TabPlacement,
  ) {
    val scaffoldState = rememberBottomSheetScaffoldState()
    val sheetState = scaffoldState.bottomSheetState
    val coroutineScope = rememberCoroutineScope()
    var playbackPeekHeight by remember { mutableStateOf(0.dp) }
    val expanded =
      sheetState.currentValue == SheetValue.Expanded ||
        sheetState.targetValue == SheetValue.Expanded
    CollapseWithoutExpandedContent(template, sheetState)
    backGestureDispatcherPresenter.PredictiveBackHandlerPresenter(
      enabled = template.playback != null && expanded,
    ) { progress ->
      progress.collect { /* Wait for the back gesture to complete. */ }
      coroutineScope.launch { sheetState.partialExpand() }
    }

    // Stable slot keys preserve renderer state when layers move or change drawing order.
    SubcomposeLayout(Modifier.fillMaxSize().clipToBounds().testTag("app-shell")) { constraints ->
      val width = constraints.maxWidth
      val height = constraints.maxHeight
      val tabsAtStart = tabPlacement != TabPlacement.BOTTOM
      val collapsedOffset = height - playbackPeekHeight.toPx()
      val cornerRadius = { playbackCornerRadius(sheetState, collapsedOffset) }
      val tabs =
        subcompose("tabs") {
            Box(
              modifier =
                Modifier.clipBehindPlayback(
                  sheetState,
                  tabsAtStart && template.playback != null,
                  cornerRadius,
                ),
              propagateMinConstraints = true,
            ) {
              CompositionLocalProvider(LocalTabPlacement provides tabPlacement) {
                template.tabs?.let { Render(it) }
              }
            }
          }
          .single()
          .measure(
            when {
              template.tabs == null -> Constraints.fixed(0, 0)
              tabsAtStart -> Constraints(maxWidth = width, minHeight = height, maxHeight = height)
              else -> Constraints(minWidth = width, maxWidth = width, maxHeight = height)
            },
          )
      val playbackBottomInset = if (tabsAtStart) 0.dp else tabs.height.toDp()
      val peekHeight = if (template.playback == null) 0.dp else playbackPeekHeight
      val body =
        subcompose("body") {
            PlaybackScaffold(
              template = template,
              scaffoldState = scaffoldState,
              peekHeight = peekHeight,
              contentStart = if (tabsAtStart) tabs.width.toDp() else 0.dp,
              playbackBottomInset = playbackBottomInset,
              collapsedOffset = collapsedOffset,
              cornerRadius = cornerRadius,
              onPeekHeightChanged = { playbackPeekHeight = it },
            )
          }
          .single()
          .measure(Constraints.fixed(width, height))

      layout(width, height) {
        if (tabsAtStart) tabs.placeRelative(0, 0)
        body.placeRelative(0, 0)
        if (!tabsAtStart) {
          val playbackTravel =
            if (template.playback == null) 0f
            else (height - peekHeight.toPx() - sheetState.requireOffset()).coerceAtLeast(0f)
          tabs.placeRelative(0, height - tabs.height + playbackTravel.roundToInt())
        }
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun CollapseWithoutExpandedContent(
    template: AppTemplate.AdaptiveTemplate,
    sheetState: SheetState,
  ) {
    val canExpand = template.playback != null && template.expandedPlayback != null
    LaunchedEffect(canExpand) {
      if (
        !canExpand &&
          (sheetState.currentValue == SheetValue.Expanded ||
            sheetState.targetValue == SheetValue.Expanded)
      ) {
        sheetState.partialExpand()
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun PlaybackScaffold(
    template: AppTemplate.AdaptiveTemplate,
    scaffoldState: BottomSheetScaffoldState,
    peekHeight: Dp,
    contentStart: Dp,
    playbackBottomInset: Dp,
    collapsedOffset: Float,
    cornerRadius: () -> Dp,
    onPeekHeightChanged: (Dp) -> Unit,
  ) {
    val sheetState = scaffoldState.bottomSheetState
    BottomSheetScaffold(
      modifier = Modifier.fillMaxSize(),
      scaffoldState = scaffoldState,
      sheetPeekHeight = peekHeight,
      sheetMaxWidth = Dp.Unspecified,
      sheetShape = RectangleShape,
      sheetContainerColor = Color.Transparent,
      sheetContentColor = AppTheme.colorScheme.onSurface,
      sheetTonalElevation = 0.dp,
      sheetShadowElevation = 0.dp,
      sheetDragHandle = null,
      sheetSwipeEnabled = template.playback != null && template.expandedPlayback != null,
      containerColor = Color.Transparent,
      sheetContent = {
        Box(if (template.playback == null) Modifier.height(0.dp) else Modifier.fillMaxSize()) {
          template.playback?.let {
            PlaybackLayer(
              collapsedContent = it,
              expandedContent = template.expandedPlayback,
              bottomInset = playbackBottomInset,
              sheetState = sheetState,
              cornerRadius = cornerRadius,
              onPeekHeightChanged = onPeekHeightChanged,
            )
          }
        }
      },
    ) { padding ->
      val contentPadding =
        PaddingValues(
          start = contentStart,
          bottom =
            if (template.playback == null) playbackBottomInset
            else padding.calculateBottomPadding(),
        )
      Box(Modifier.fillMaxSize()) {
        // The Android renderer bridge does not forward modifiers to child renderers.
        Box(
          Modifier.fillMaxSize()
            .clipBehindPlayback(sheetState, template.playback != null, cornerRadius)
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
          Render(template.content, Modifier.fillMaxSize())
        }
        if (template.playback != null) {
          // Only the top shadow is visible. Cache a short, fixed outline and move its layer.
          Spacer(
            Modifier.fillMaxWidth()
              .height(48.dp)
              .graphicsLayer {
                // Compose can query layer semantics before Material initializes the anchors.
                translationY =
                  if (sheetState.hasPartiallyExpandedState) sheetState.requireOffset()
                  else collapsedOffset
                alpha = cornerRadius() / 24.dp
                compositingStrategy = CompositingStrategy.ModulateAlpha
              }
              .appLayerShadow(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
          )
        }
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  private fun playbackCornerRadius(sheetState: SheetState, collapsedOffset: Float): Dp =
    if (sheetState.hasExpandedState && collapsedOffset > 0f) {
      24.dp * (sheetState.requireOffset() / collapsedOffset).coerceIn(0f, 1f)
    } else 24.dp

  @OptIn(ExperimentalMaterial3Api::class)
  private fun Modifier.clipBehindPlayback(
    sheetState: SheetState,
    enabled: Boolean,
    cornerRadius: () -> Dp,
  ): Modifier {
    if (!enabled) {
      return this
    }
    return drawWithContent {
      val bottom = sheetState.requireOffset().coerceAtLeast(0f) + cornerRadius().toPx()
      if (bottom > 0f) {
        clipRect(bottom = bottom) { this@drawWithContent.drawContent() }
      }
    }
  }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  private fun PlaybackLayer(
    collapsedContent: BaseModel,
    expandedContent: BaseModel?,
    bottomInset: Dp,
    sheetState: SheetState,
    cornerRadius: () -> Dp,
    onPeekHeightChanged: (Dp) -> Unit,
  ) {
    val coroutineScope = rememberCoroutineScope()
    val expanded =
      expandedContent != null &&
        (sheetState.currentValue == SheetValue.Expanded ||
          sheetState.targetValue == SheetValue.Expanded)
    val onExpand: () -> Unit = { coroutineScope.launch { sheetState.expand() } }
    val onCollapse: () -> Unit = { coroutineScope.launch { sheetState.partialExpand() } }
    val currentCornerRadius by rememberUpdatedState(cornerRadius)
    val transition = remember { PlaybackTransition { 1f - currentCornerRadius() / 24.dp } }
    val density = LocalDensity.current
    val surfaceColor = AppTheme.colorScheme.surface
    val borderColor = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
      Modifier.fillMaxSize()
        .testTag("playback-sheet")
        // Keep per-frame position reads out of composition and measurement.
        .graphicsLayer {
          val radius = cornerRadius()
          shape = RoundedCornerShape(topStart = radius, topEnd = radius)
          clip = true
        }
        .background(surfaceColor)
        .drawWithCache {
          val path = Path()
          val stroke = Stroke(1.dp.toPx())
          val inset = stroke.width / 2f
          onDrawWithContent {
            drawContent()
            val radius = CornerRadius((cornerRadius().toPx() - inset).coerceAtLeast(0f))
            path.reset()
            path.addRoundRect(
              RoundRect(
                rect = Rect(inset, inset, size.width - inset, size.height - inset),
                topLeft = radius,
                topRight = radius,
              ),
            )
            drawPath(path, borderColor, style = stroke)
          }
        }
        .indication(interactionSource, LocalIndication.current),
    ) {
      Box(
        Modifier.fillMaxWidth()
          .onSizeChanged { onPeekHeightChanged(with(density) { it.height.toDp() }) }
          .then(with(transition) { Modifier.fade(expanded = false) })
          .testTag("playback")
          .clickable(
            enabled = !expanded && expandedContent != null,
            interactionSource = interactionSource,
            indication = null,
            role = Role.Button,
            onClick = onExpand,
          )
          .semantics {
            if (!expanded && expandedContent != null)
              expand {
                onExpand()
                true
              }
          }
          .padding(bottom = bottomInset)
          .consumeWindowInsets(PaddingValues(bottom = bottomInset))
          .windowInsetsPadding(
            WindowInsets.safeDrawing.only(
              WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
            ),
          ),
      ) {
        CompositionLocalProvider(LocalPlaybackTransition provides transition) {
          Render(collapsedContent)
        }
      }
      if (expandedContent != null) {
        ExpandedPlaybackContent(expandedContent, transition, expanded, onCollapse)
      }
    }
  }

  @Composable
  private fun ExpandedPlaybackContent(
    content: BaseModel,
    transition: PlaybackTransition,
    expanded: Boolean,
    onCollapse: () -> Unit,
  ) {
    val visible by remember(transition) { derivedStateOf { transition.fraction > 0f } }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded, visible) {
      if (expanded && visible) {
        focusRequester.requestFocus()
      }
    }
    Box(
      Modifier.fillMaxSize()
        .then(
          if (visible) Modifier.testTag("playback-screen") else Modifier.clearAndSetSemantics {},
        )
        .layout { measurable, constraints ->
          val content = measurable.measure(constraints)
          layout(content.width, content.height) {
            if (transition.fraction > 0f) {
              content.placeRelative(0, 0)
            }
          }
        }
        .focusRequester(focusRequester)
        .onPreviewKeyEvent {
          if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
            onCollapse()
            true
          } else false
        }
        .focusable(enabled = visible)
        .semantics {
          if (visible) {
            collapse {
              onCollapse()
              true
            }
          }
        },
    ) {
      Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        CompositionLocalProvider(
          LocalCollapsePlayback provides onCollapse,
          LocalPlaybackTransition provides transition,
        ) {
          Render(content, Modifier.fillMaxSize())
        }
      }
    }
  }
}
