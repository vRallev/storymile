package software.ralf.storymile.templates

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
      val screenSize = LocalScreenSize.current
      val tabPlacement =
        when {
          screenSize.width >= 1200.dp -> TabPlacement.START_EXPANDED
          screenSize.category == ScreenSize.Category.PHONE -> TabPlacement.BOTTOM
          else -> TabPlacement.START
        }

      AppShell(template, tabPlacement)
      template.overlay?.let { Render(it, Modifier.matchParentSize()) }
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
      enabled = template.playback != null && expanded
    ) { progress ->
      progress.collect { /* Wait for the back gesture to complete. */ }
      coroutineScope.launch { sheetState.partialExpand() }
    }

    // Stable slot keys preserve renderer state when layers move or change drawing order.
    SubcomposeLayout(Modifier.fillMaxSize().clipToBounds()) { constraints ->
      val width = constraints.maxWidth
      val height = constraints.maxHeight
      val tabsAtStart = tabPlacement != TabPlacement.BOTTOM
      val tabs =
        subcompose("tabs") {
            Box(propagateMinConstraints = true) {
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
            }
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
    onPeekHeightChanged: (Dp) -> Unit,
  ) {
    val sheetState = scaffoldState.bottomSheetState
    var height by remember { mutableIntStateOf(0) }
    val collapsedOffset = with(LocalDensity.current) { height - peekHeight.toPx() }
    val cornerRadius =
      if (sheetState.hasExpandedState && collapsedOffset > 0f) {
        // Follow the sheet during both a held drag and its settling animation.
        24.dp * (sheetState.requireOffset() / collapsedOffset).coerceIn(0f, 1f)
      } else 24.dp
    val shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius)
    BottomSheetScaffold(
      modifier = Modifier.fillMaxSize().onSizeChanged { height = it.height },
      scaffoldState = scaffoldState,
      sheetPeekHeight = peekHeight,
      sheetMaxWidth = Dp.Unspecified,
      sheetShape = shape,
      sheetContainerColor = AppTheme.colorScheme.surface,
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
              shape = shape,
              onPeekHeightChanged = onPeekHeightChanged,
            )
          }
        }
      },
    ) { padding ->
      Box(Modifier.fillMaxSize()) {
        Render(
          template.content,
          Modifier.fillMaxSize()
            .padding(
              start = contentStart,
              bottom =
                if (template.playback == null) playbackBottomInset
                else padding.calculateBottomPadding(),
            ),
        )
        if (template.playback != null) {
          // Draw the theme's soft shadow behind Material's moving sheet.
          Spacer(
            Modifier.fillMaxSize()
              .offset {
                IntOffset(
                  0,
                  scaffoldState.bottomSheetState.requireOffset().roundToInt(),
                )
              }
              .appLayerShadow(shape)
          )
        }
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
    shape: Shape,
    onPeekHeightChanged: (Dp) -> Unit,
  ) {
    val coroutineScope = rememberCoroutineScope()
    val expanded =
      expandedContent != null &&
        (sheetState.currentValue == SheetValue.Expanded ||
          sheetState.targetValue == SheetValue.Expanded)
    val onExpand: () -> Unit = { coroutineScope.launch { sheetState.expand() } }
    val onCollapse: () -> Unit = { coroutineScope.launch { sheetState.partialExpand() } }
    val density = LocalDensity.current
    val border = BorderStroke(1.dp, AppTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(expanded) {
      if (expanded) focusRequester.requestFocus()
    }
    Box(
      Modifier.fillMaxSize()
        .testTag("playback-sheet")
        .border(border, shape)
        .indication(interactionSource, LocalIndication.current)
    ) {
      Box(
        Modifier.fillMaxWidth()
          .onSizeChanged { onPeekHeightChanged(with(density) { it.height.toDp() }) }
          .layout { measurable, constraints ->
            val bar = measurable.measure(constraints)
            layout(bar.width, bar.height) {
              // Keep measuring while expanded, but do not draw or expose the bar to input.
              if (!expanded) bar.placeRelative(0, 0)
            }
          }
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
      ) {
        Render(collapsedContent)
      }
      if (expanded) {
        Box(
          Modifier.fillMaxSize()
            .testTag("playback-screen")
            .focusRequester(focusRequester)
            .onPreviewKeyEvent {
              if (it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                onCollapse()
                true
              } else false
            }
            .focusable()
            .semantics {
              collapse {
                onCollapse()
                true
              }
            }
        ) {
          Render(expandedContent, Modifier.fillMaxSize())
        }
      }
    }
  }
}
