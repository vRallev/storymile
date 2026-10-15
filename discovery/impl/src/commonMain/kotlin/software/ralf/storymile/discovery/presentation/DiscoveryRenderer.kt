package software.ralf.storymile.discovery.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.ExperimentalAppPlatform
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.app.platform.renderer.text.rememberPresenterBackedTextFieldState
import software.ralf.storymile.discovery.DiscoveredPodcast
import software.ralf.storymile.discovery.impl.generated.resources.Res
import software.ralf.storymile.discovery.impl.generated.resources.browse_topics
import software.ralf.storymile.discovery.impl.generated.resources.clear_search
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
import software.ralf.storymile.discovery.impl.generated.resources.search_podcasts
import software.ralf.storymile.discovery.impl.generated.resources.search_results
import software.ralf.storymile.discovery.impl.generated.resources.topic_culture_subtitle
import software.ralf.storymile.discovery.impl.generated.resources.topic_nature_subtitle
import software.ralf.storymile.discovery.impl.generated.resources.topic_science_subtitle
import software.ralf.storymile.discovery.impl.generated.resources.topic_technology_subtitle
import software.ralf.storymile.discovery.impl.generated.resources.try_another_search
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

  @Composable
  override fun Compose(model: DiscoveryPresenterImpl.Model, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxSize().testTag("discovery-screen")) {
      val wide = maxWidth / LocalDensity.current.fontScale >= 760.dp
      val padding = if (maxWidth < 600.dp) 16.dp else 32.dp
      val listState = rememberLazyListState()
      var requestedFocus by rememberSaveable { mutableStateOf(false) }
      LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("discovery-list"),
        state = listState,
        contentPadding = PaddingValues(horizontal = padding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
      ) {
        item {
          DiscoveryHeader(
            title = stringResource(model.topic?.titleResource ?: Res.string.discover_podcasts),
            backTag = "discovery-back",
            onBack = model.onBack,
          )
        }
        item {
          if (model.topic == null) {
            Search(model, model.focusSearch && !requestedFocus) { requestedFocus = true }
          } else {
            Topic(model)
          }
        }
        if (model.topic == null) {
          item { Topics(model) }
        }
        when (val state = model.state) {
          CatalogState.Loading -> item { Loading() }
          CatalogState.Error -> item { Error(model.onRetry) }
          is CatalogState.Ready -> results(model, state.podcasts, wide)
        }
      }
    }
  }

  private fun LazyListScope.results(
    model: DiscoveryPresenterImpl.Model,
    podcasts: List<DiscoveredPodcast>,
    wide: Boolean,
  ) {
    if (podcasts.isEmpty()) {
      item { Empty() }
      return
    }
    val featured = model.topic == null && model.query.value.isBlank()
    val showEpisodes = featured || model.topic != null
    item { FirstResults(model, podcasts.take(2), featured, wide && showEpisodes) }
    if (!wide && showEpisodes) {
      item {
        SampleEpisodes(
          title = stringResource(Res.string.episodes_to_try),
          podcasts = podcasts.take(2),
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
    items(podcasts.drop(2).chunked(if (featured) 2 else 1), key = { it.first().id }) { remaining ->
      val resultModifier =
        if (wide && showEpisodes) {
          Modifier.fillMaxWidth(0.52f)
        } else {
          Modifier.fillMaxWidth()
        }
      Column(resultModifier) { FirstPodcasts(remaining, featured, model.onPodcastSelected) }
    }
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
        ResultsTitle(model, featured)
        FirstPodcasts(podcasts, featured, model.onPodcastSelected)
      }
      if (showEpisodesBeside) {
        SampleEpisodes(
          title = stringResource(Res.string.episodes_to_try),
          podcasts = podcasts,
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
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
      state = query,
      modifier = Modifier.fillMaxWidth().focusRequester(focusRequester).testTag("discovery-search"),
      placeholder = { Text(stringResource(Res.string.search_podcasts)) },
      leadingIcon = { Icon(Icons.Outlined.Search, null) },
      trailingIcon = {
        if (query.text.isNotEmpty()) {
          IconButton(
            onClick = { query.clearText() },
            modifier = Modifier.testTag("discovery-clear"),
          ) {
            Icon(Icons.Filled.Close, stringResource(Res.string.clear_search))
          }
        }
      },
      lineLimits = TextFieldLineLimits.SingleLine,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      onKeyboardAction = { focusManager.clearFocus() },
      shape = RoundedCornerShape(12.dp),
      colors =
        OutlinedTextFieldDefaults.colors(
          unfocusedBorderColor = AppTheme.colorScheme.outlineVariant,
          unfocusedContainerColor = AppTheme.colorScheme.surface,
          focusedContainerColor = AppTheme.colorScheme.surface,
        ),
    )
    LaunchedEffect(requestFocus) {
      if (requestFocus) {
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
  private fun ResultsTitle(model: DiscoveryPresenterImpl.Model, featured: Boolean) {
    SectionTitle(
      stringResource(
        when {
          featured -> Res.string.featured_podcasts
          model.topic != null -> Res.string.podcasts_to_explore
          else -> Res.string.search_results
        },
      ),
    )
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
    onSelected: (DiscoveredPodcast) -> Unit,
  ) {
    if (featured) {
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth / LocalDensity.current.fontScale < 300.dp) {
          Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            podcasts.forEach { podcast ->
              FeaturedPodcast(podcast, onSelected, Modifier.fillMaxWidth())
            }
          }
        } else {
          Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            podcasts.forEach { podcast ->
              FeaturedPodcast(podcast, onSelected, Modifier.weight(1f))
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
          PodcastRow(podcast, onClick = { onSelected(podcast) })
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
      shape = RoundedCornerShape(12.dp),
      color = Color.Transparent,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        PodcastArtwork(podcast, Modifier.fillMaxWidth().aspectRatio(1f))
        Text(
          text = podcast.title,
          style = AppTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
        podcast.author?.let { author ->
          Text(
            text = author,
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
}
