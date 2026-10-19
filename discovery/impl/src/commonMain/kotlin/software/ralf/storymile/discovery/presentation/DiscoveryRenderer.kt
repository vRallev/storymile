package software.ralf.storymile.discovery.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.ExperimentalAppPlatform
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.text.rememberPresenterBackedTextFieldState
import software.ralf.storymile.design.SearchField
import software.ralf.storymile.design.TitleBar
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.browse_topics
import software.ralf.storymile.discovery.impl.generated.resources.discover_podcasts
import software.ralf.storymile.discovery.impl.generated.resources.episodes_to_try
import software.ralf.storymile.discovery.impl.generated.resources.featured_podcasts
import software.ralf.storymile.discovery.impl.generated.resources.filter_all_nature
import software.ralf.storymile.discovery.impl.generated.resources.filter_oceans
import software.ralf.storymile.discovery.impl.generated.resources.filter_outdoors
import software.ralf.storymile.discovery.impl.generated.resources.load_error
import software.ralf.storymile.discovery.impl.generated.resources.loading_podcasts
import software.ralf.storymile.discovery.impl.generated.resources.no_podcasts
import software.ralf.storymile.discovery.impl.generated.resources.podcasts_to_explore
import software.ralf.storymile.discovery.impl.generated.resources.retry
import software.ralf.storymile.discovery.impl.generated.resources.search_results
import software.ralf.storymile.discovery.impl.generated.resources.topic_culture_subtitle
import software.ralf.storymile.discovery.impl.generated.resources.topic_nature_subtitle
import software.ralf.storymile.discovery.impl.generated.resources.topic_science_subtitle
import software.ralf.storymile.discovery.impl.generated.resources.topic_technology_subtitle
import software.ralf.storymile.discovery.impl.generated.resources.try_another_search
import software.ralf.storymile.discovery.presentation.PodcastSharedElement.Companion.podcastSharedElement
import software.ralf.storymile.templates.LocalAnimatedVisibilityScope
import software.ralf.storymile.templates.SharedNavigationElement
import software.ralf.storymile.templates.sharedNavigationBounds
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class DiscoveryRenderer : ComposeRenderer<DiscoveryPresenterImpl.Model>() {
  private val DiscoveryTopic.subtitleResource: StringResource
    get() =
      when (this) {
        DiscoveryTopic.NATURE -> Res.string.topic_nature_subtitle
        DiscoveryTopic.SCIENCE -> Res.string.topic_science_subtitle
        DiscoveryTopic.CULTURE -> Res.string.topic_culture_subtitle
        DiscoveryTopic.TECHNOLOGY -> Res.string.topic_technology_subtitle
      }

  private val DiscoveryTopic.Filter.labelResource: StringResource
    get() =
      when (this) {
        DiscoveryTopic.Filter.ALL_NATURE -> Res.string.filter_all_nature
        DiscoveryTopic.Filter.OCEANS -> Res.string.filter_oceans
        DiscoveryTopic.Filter.OUTDOORS -> Res.string.filter_outdoors
      }

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Compose(model: DiscoveryPresenterImpl.Model, modifier: Modifier) {
    BoxWithConstraints(
      modifier.fillMaxSize().discoveryScreenTransition().clipToBounds().testTag("discovery-screen"),
    ) {
      val wide = maxWidth / LocalDensity.current.fontScale >= 760.dp
      val padding = if (maxWidth < 600.dp) 16.dp else 32.dp
      val listState = rememberLazyListState()
      var requestedFocus by rememberSaveable { mutableStateOf(false) }
      val floatingSearch = model.topic == null
      var headerHeight by rememberSaveable { mutableIntStateOf(0) }
      val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
      val scrollConnection =
        remember(scrollBehavior) {
          object : NestedScrollConnection by scrollBehavior.nestedScrollConnection {
            // Let the list scroll beneath the returning header.
            override fun onPreScroll(available: Offset, source: NestedScrollSource) = Offset.Zero
          }
        }
      LaunchedEffect(listState.canScrollBackward) {
        if (!listState.canScrollBackward) {
          scrollBehavior.state.heightOffset = 0f
        }
      }
      Column(Modifier.fillMaxSize()) {
        if (!floatingSearch) {
          TitleBar(
            title = stringResource(requireNotNull(model.topic).titleResource),
            onBack = model.onBack,
            backButtonModifier = Modifier.testTag("discovery-back"),
          )
        }
        LazyColumn(
          modifier =
            Modifier.weight(1f)
              .then(if (floatingSearch) Modifier.nestedScroll(scrollConnection) else Modifier)
              .testTag("discovery-list"),
          state = listState,
          contentPadding =
            PaddingValues(
              start = padding,
              end = padding,
              top =
                if (floatingSearch) with(LocalDensity.current) { headerHeight.toDp() } else 16.dp,
              bottom = 16.dp,
            ),
          verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
          catalogItems(model, wide)
        }
      }
      if (floatingSearch) {
        Surface(
          modifier =
            Modifier.fillMaxWidth()
              .offset { IntOffset(0, scrollBehavior.state.heightOffset.roundToInt()) }
              .onSizeChanged {
                headerHeight = it.height
                scrollBehavior.state.heightOffsetLimit = -it.height.toFloat()
                scrollBehavior.state.heightOffset =
                  scrollBehavior.state.heightOffset.coerceIn(-it.height.toFloat(), 0f)
              }
              .testTag("discovery-search-header"),
          color = AppTheme.colorScheme.background,
        ) {
          Column {
            TitleBar(
              title = stringResource(Res.string.discover_podcasts),
              onBack = model.onBack,
              backButtonModifier = Modifier.testTag("discovery-back"),
              titleModifier =
                Modifier.sharedNavigationBounds(SharedNavigationElement.APP_BAR_TITLE),
              profileIconModifier =
                Modifier.sharedNavigationBounds(SharedNavigationElement.PROFILE_ICON)
                  .testTag("discovery-profile-icon"),
            )
            Box(Modifier.padding(horizontal = padding).padding(top = 16.dp, bottom = 20.dp)) {
              Search(model, model.focusSearch && !requestedFocus) { requestedFocus = true }
            }
          }
        }
      }
    }
  }

  private fun LazyListScope.catalogItems(model: DiscoveryPresenterImpl.Model, wide: Boolean) {
    val featured = model.topic == null && model.query.value.isBlank()
    item(key = "catalog") {
      Column {
        if (model.topic == null) {
          AnimatedVisibility(
            visible = featured,
            modifier = Modifier.testTag("discovery-topics"),
            enter =
              expandVertically(tween(200), expandFrom = Alignment.Top) +
                fadeIn(tween(FADE_DURATION_MILLIS)),
            exit =
              shrinkVertically(tween(200), shrinkTowards = Alignment.Top) +
                fadeOut(tween(FADE_DURATION_MILLIS)),
          ) {
            Column(Modifier.padding(bottom = 20.dp)) { Topics(model) }
          }
        } else {
          Column(Modifier.padding(bottom = 20.dp)) { Topic(model) }
        }
        Catalog(model, featured, wide)
      }
    }
    val state = model.state
    if (state is CatalogState.Ready && state.podcasts.isNotEmpty()) {
      results(model, state.podcasts, featured, wide)
    }
  }

  @Composable
  private fun Catalog(model: DiscoveryPresenterImpl.Model, featured: Boolean, wide: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
      Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
        ResultsTitle(model, featured, Modifier.weight(1.1f))
        if (wide) {
          AnimatedVisibility(
            visible =
              (featured || model.topic != null) &&
                (model.state as? CatalogState.Ready)?.podcasts?.isNotEmpty() == true,
            modifier = Modifier.weight(1f),
            enter = fadeIn(tween(FADE_DURATION_MILLIS)),
            exit = fadeOut(tween(FADE_DURATION_MILLIS)),
          ) {
            SectionTitle(stringResource(Res.string.episodes_to_try))
          }
        }
      }
      Crossfade(
        targetState = CatalogContent(model.state, featured),
        animationSpec = tween(FADE_DURATION_MILLIS),
        label = "discovery-content",
      ) { content ->
        when (val state = content.state) {
          CatalogState.Loading -> Loading()
          CatalogState.Error -> Error(model.onRetry)
          is CatalogState.Ready -> {
            if (state.podcasts.isEmpty()) {
              Empty()
            } else {
              FirstResults(
                model,
                state.podcasts.take(2),
                content.featured,
                wide && (content.featured || model.topic != null),
              )
            }
          }
        }
      }
    }
  }

  private fun LazyListScope.results(
    model: DiscoveryPresenterImpl.Model,
    podcasts: List<DiscoveredPodcast>,
    featured: Boolean,
    wide: Boolean,
  ) {
    val showEpisodes = featured || model.topic != null
    if (!wide && showEpisodes) {
      item(key = "episodes") {
        Episodes(
          title = stringResource(Res.string.episodes_to_try),
          model = model.episodes,
          modifier = Modifier.fadeItem(this).fillMaxWidth(),
        )
      }
    }
    items(
      podcasts.drop(2).chunked(if (featured) 2 else 1),
      key = { "${if (featured) "featured" else "results"}-${it.first().id}" },
    ) { remaining ->
      val resultModifier =
        if (wide && showEpisodes) {
          Modifier.fadeItem(this).fillMaxWidth(0.52f)
        } else {
          Modifier.fadeItem(this).fillMaxWidth()
        }
      Column(resultModifier) { FirstPodcasts(remaining, featured, model) }
    }
  }

  private fun Modifier.fadeItem(scope: LazyItemScope): Modifier =
    with(scope) {
      animateItem(
        fadeInSpec = tween(FADE_DURATION_MILLIS),
        placementSpec = null,
        fadeOutSpec = tween(FADE_DURATION_MILLIS),
      )
    }

  @Composable
  private fun FirstResults(
    model: DiscoveryPresenterImpl.Model,
    podcasts: List<DiscoveredPodcast>,
    featured: Boolean,
    showEpisodesBeside: Boolean,
  ) {
    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
      Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FirstPodcasts(podcasts, featured, model)
      }
      if (showEpisodesBeside) {
        Episodes(
          title = null,
          model = model.episodes,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }

  @OptIn(ExperimentalAppPlatform::class)
  @Composable
  private fun Search(
    model: DiscoveryPresenterImpl.Model,
    requestFocus: Boolean,
    onFocused: () -> Unit,
  ) {
    val query = rememberPresenterBackedTextFieldState(model.query)
    val focusRequester = remember { FocusRequester() }
    val visibilityScope = LocalAnimatedVisibilityScope.current
    SearchField(
      state = query,
      modifier = Modifier.sharedNavigationBounds(SharedNavigationElement.SEARCH_FIELD),
      fieldModifier = Modifier.focusRequester(focusRequester).testTag("discovery-search"),
      placeholderModifier = Modifier.testTag("discovery-search-placeholder"),
      clearButtonModifier = Modifier.testTag("discovery-clear"),
    )
    LaunchedEffect(requestFocus) {
      if (requestFocus) {
        // Keep keyboard insets from moving the shared elements during the screen transition.
        if (visibilityScope != null) {
          snapshotFlow {
            visibilityScope.transition.currentState == EnterExitState.Visible &&
              visibilityScope.transition.targetState == EnterExitState.Visible &&
              !visibilityScope.transition.isRunning
          }
            .first { it }
        }
        focusRequester.requestFocus()
        onFocused()
      }
    }
  }

  @Composable
  private fun Topics(model: DiscoveryPresenterImpl.Model) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionTitle(stringResource(Res.string.browse_topics))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiscoveryTopic.entries.forEach { topic ->
          FilterChip(
            selected = false,
            onClick = { model.onTopicSelected(topic) },
            label = { Text(stringResource(topic.titleResource)) },
            modifier = Modifier.testTag("discovery-topic-${topic.name}"),
            shape = CircleShape,
          )
        }
      }
    }
  }

  @Composable
  private fun Topic(model: DiscoveryPresenterImpl.Model) {
    val topic = requireNotNull(model.topic)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(
        text = stringResource(topic.subtitleResource),
        style = AppTheme.typography.bodyLarge,
        color = AppTheme.colorScheme.onSurfaceVariant,
      )
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        topic.filters.forEach { filter ->
          FilterChip(
            selected = model.selectedFilter == filter,
            onClick = { model.onFilterSelected(filter) },
            label = { Text(stringResource(filter.labelResource)) },
            modifier = Modifier.testTag("discovery-filter-${filter.name}"),
            shape = CircleShape,
          )
        }
      }
    }
  }

  @Composable
  private fun ResultsTitle(
    model: DiscoveryPresenterImpl.Model,
    featured: Boolean,
    modifier: Modifier,
  ) {
    Crossfade(
      targetState =
        stringResource(
          when {
            featured -> Res.string.featured_podcasts
            model.topic != null -> Res.string.podcasts_to_explore
            else -> Res.string.search_results
          },
        ),
      modifier = modifier.testTag("discovery-results-title"),
      animationSpec = tween(FADE_DURATION_MILLIS),
      label = "discovery-results-title",
    ) { title ->
      SectionTitle(title)
    }
  }

  @Composable
  private fun SectionTitle(title: String) {
    Text(
      text = title,
      modifier = Modifier.semantics { heading() },
      style = AppTheme.typography.titleLarge,
      fontWeight = FontWeight.SemiBold,
    )
  }

  @Composable
  private fun FirstPodcasts(
    podcasts: List<DiscoveredPodcast>,
    featured: Boolean,
    model: DiscoveryPresenterImpl.Model,
  ) {
    val source =
      model.topic?.let { ShowPresenter.Source.Topic(it) }
        ?: if (featured) ShowPresenter.Source.Browse else ShowPresenter.Source.Search
    if (featured) {
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth / LocalDensity.current.fontScale < 300.dp) {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            podcasts.forEach { podcast ->
              FeaturedPodcast(podcast, model.onPodcastSelected, Modifier.fillMaxWidth())
            }
          }
        } else {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            podcasts.forEach { podcast ->
              FeaturedPodcast(podcast, model.onPodcastSelected, Modifier.weight(1f))
            }
            if (podcasts.size == 1) {
              Spacer(Modifier.weight(1f))
            }
          }
        }
      }
    } else {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        podcasts.forEach { podcast ->
          PodcastRow(
            podcast,
            onClick = { model.onPodcastSelected(podcast) },
            transitionSource = source,
          )
        }
      }
    }
  }

  @Composable
  private fun FeaturedPodcast(
    podcast: DiscoveredPodcast,
    onSelected: (DiscoveredPodcast) -> Unit,
    modifier: Modifier,
  ) {
    Surface(
      onClick = { onSelected(podcast) },
      modifier = modifier.testTag("discovery-podcast-${podcast.id}"),
      shape = RoundedCornerShape(16.dp),
      color = Color.Transparent,
    ) {
      Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PodcastArtwork(
          podcast,
          Modifier.podcastSharedElement(
              podcast,
              ShowPresenter.Source.Browse,
              PodcastSharedElement.Artwork,
            )
            .testTag("discovery-artwork-${podcast.id}")
            .fillMaxWidth()
            .aspectRatio(1f),
        )
        Text(
          text = podcast.title,
          modifier =
            Modifier.podcastSharedElement(
                podcast,
                ShowPresenter.Source.Browse,
                PodcastSharedElement.Title,
              )
              .testTag("discovery-title-${podcast.id}"),
          style = AppTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        podcast.author?.let { author ->
          Text(
            text = author,
            modifier =
              Modifier.podcastSharedElement(
                  podcast,
                  ShowPresenter.Source.Browse,
                  PodcastSharedElement.Author,
                )
                .testTag("discovery-author-${podcast.id}"),
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }

  @Composable
  private fun Loading() {
    Column(
      modifier = Modifier.fillMaxWidth().padding(32.dp).testTag("discovery-loading"),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      CircularProgressIndicator()
      Text(stringResource(Res.string.loading_podcasts))
    }
  }

  @Composable
  private fun Error(onRetry: () -> Unit) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(stringResource(Res.string.load_error))
      Button(onClick = onRetry, modifier = Modifier.testTag("discovery-retry")) {
        Text(stringResource(Res.string.retry))
      }
    }
  }

  @Composable
  private fun Empty() {
    Column(
      modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp).testTag("discovery-empty"),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(stringResource(Res.string.no_podcasts), style = AppTheme.typography.titleLarge)
      Text(
        stringResource(Res.string.try_another_search),
        color = AppTheme.colorScheme.onSurfaceVariant,
      )
    }
  }

  private companion object {
    const val FADE_DURATION_MILLIS = 120
  }

  private data class CatalogContent(val state: CatalogState, val featured: Boolean)
}
