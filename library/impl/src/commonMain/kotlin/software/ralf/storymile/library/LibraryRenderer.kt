package software.ralf.storymile.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab as MaterialTab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.design.TabHeader
import software.ralf.storymile.library.LibraryPresenterImpl.Tab
import software.ralf.storymile.library.impl.generated.resources.Res
import software.ralf.storymile.library.impl.generated.resources.library_discover
import software.ralf.storymile.library.impl.generated.resources.library_history
import software.ralf.storymile.library.impl.generated.resources.library_history_empty_description
import software.ralf.storymile.library.impl.generated.resources.library_history_empty_title
import software.ralf.storymile.library.impl.generated.resources.library_history_illustration
import software.ralf.storymile.library.impl.generated.resources.library_illustration
import software.ralf.storymile.library.impl.generated.resources.library_queue
import software.ralf.storymile.library.impl.generated.resources.library_queue_empty_description
import software.ralf.storymile.library.impl.generated.resources.library_queue_empty_title
import software.ralf.storymile.library.impl.generated.resources.library_queue_illustration
import software.ralf.storymile.library.impl.generated.resources.library_saved
import software.ralf.storymile.library.impl.generated.resources.library_saved_empty_description
import software.ralf.storymile.library.impl.generated.resources.library_saved_empty_title
import software.ralf.storymile.library.impl.generated.resources.library_shows
import software.ralf.storymile.library.impl.generated.resources.library_shows_empty_description
import software.ralf.storymile.library.impl.generated.resources.library_shows_empty_title
import software.ralf.storymile.library.impl.generated.resources.library_shows_illustration
import software.ralf.storymile.library.impl.generated.resources.library_title
import software.ralf.storymile.screen.LocalScreenSize
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class LibraryRenderer : ComposeRenderer<LibraryPresenterImpl.Model>() {
  @Composable
  override fun Compose(model: LibraryPresenterImpl.Model, modifier: Modifier) {
    BoxWithConstraints(
      modifier.fillMaxSize().background(AppTheme.colorScheme.background).testTag("library-empty"),
    ) {
      val compact = maxWidth < 600.dp
      val short = maxHeight < 480.dp
      val horizontal = !compact && short
      val large = maxWidth >= 1000.dp && !short
      val sidePadding = if (compact) 20.dp else 32.dp
      Column(Modifier.fillMaxSize()) {
        TabHeader(
          title = stringResource(Res.string.library_title),
          windowWidth = LocalScreenSize.current.width,
        )
        val pagerState =
          rememberPagerState(initialPage = model.selectedTab.ordinal) { Tab.entries.size }
        val currentModel by rememberUpdatedState(model)
        LaunchedEffect(model.selectedTab) {
          pagerState.animateScrollToPage(model.selectedTab.ordinal)
        }
        LaunchedEffect(pagerState) {
          snapshotFlow { pagerState.settledPage }
            .collect { page ->
              val tab = Tab.entries[page]
              if (tab != currentModel.selectedTab) {
                currentModel.onSelectTab(tab)
              }
            }
        }
        Column(Modifier.weight(1f)) {
          Tabs(
            model = model,
            pagerState = pagerState,
            modifier = Modifier.padding(horizontal = sidePadding),
          )
          HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("library-pager"),
            key = { Tab.entries[it].name },
          ) { page ->
            val tab = Tab.entries[page]
            val shows = model.shows
            if (tab == Tab.SHOWS && shows != null && (shows.loading || shows.failed || shows.shows.isNotEmpty())) {
              LibraryShows(
                model = shows,
                active = model.selectedTab == Tab.SHOWS,
                modifier = Modifier.fillMaxSize().padding(horizontal = sidePadding),
              )
            } else {
            EmptyPage(
              tab = tab,
              onDiscoverPodcasts = model.onDiscoverPodcasts,
              compact = compact,
              short = short,
              horizontal = horizontal,
              large = large,
              modifier = Modifier.fillMaxSize().padding(horizontal = sidePadding),
            )
            }
          }
        }
      }
    }
  }

  @Composable
  private fun EmptyPage(
    tab: Tab,
    onDiscoverPodcasts: () -> Unit,
    compact: Boolean,
    short: Boolean,
    horizontal: Boolean,
    large: Boolean,
    modifier: Modifier,
  ) {
    Box(modifier.testTag("library-page-${tab.name.lowercase()}")) {
      if (horizontal) {
        Row(
          modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Illustration(
            tab = tab,
            modifier =
              Modifier.padding(end = 24.dp)
                .size(if (tab == Tab.HISTORY) 80.dp else 144.dp)
                .testTag("library-illustration"),
          )
          EmptyMessage(
            tab = tab,
            onDiscoverPodcasts = onDiscoverPodcasts,
            compact = true,
            large = false,
            horizontal = true,
            modifier = Modifier.weight(1f, fill = false),
          )
        }
      } else {
        Column(
          modifier = Modifier.fillMaxSize().padding(vertical = if (short) 8.dp else 24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Illustration(
            tab = tab,
            modifier =
              Modifier.weight(1f, fill = false)
                .size(
                  when {
                    tab == Tab.HISTORY -> if (compact) 80.dp else if (large) 112.dp else 96.dp
                    compact -> 164.dp
                    large -> 256.dp
                    else -> 216.dp
                  },
                )
                .testTag("library-illustration"),
          )
          Spacer(Modifier.height(if (short) 8.dp else 24.dp))
          EmptyMessage(
            tab = tab,
            onDiscoverPodcasts = onDiscoverPodcasts,
            compact = compact,
            large = large,
            horizontal = false,
          )
        }
      }
    }
  }

  @Composable
  private fun Tabs(model: LibraryPresenterImpl.Model, pagerState: PagerState, modifier: Modifier) {
    PrimaryTabRow(
      selectedTabIndex = pagerState.currentPage,
      modifier = modifier,
      containerColor = AppTheme.colorScheme.background,
    ) {
      Tab.entries.forEach { tab ->
        MaterialTab(
          selected = pagerState.currentPage == tab.ordinal,
          onClick = { model.onSelectTab(tab) },
          selectedContentColor = AppTheme.colorScheme.primary,
          unselectedContentColor = AppTheme.colorScheme.onSurfaceVariant,
          modifier =
            Modifier.testTag("library-tab-${tab.name.lowercase()}")
              .heightIn(min = 72.dp)
              .padding(horizontal = 4.dp, vertical = 8.dp),
        ) {
          Icon(
            imageVector =
              when (tab) {
                Tab.QUEUE -> Icons.AutoMirrored.Filled.FormatListBulleted
                Tab.SHOWS -> Icons.Default.Podcasts
                Tab.SAVED -> Icons.Default.Bookmark
                Tab.HISTORY -> Icons.Default.History
              },
            contentDescription = null,
          )
          Spacer(Modifier.height(8.dp))
          Text(
            text =
              stringResource(
                when (tab) {
                  Tab.QUEUE -> Res.string.library_queue
                  Tab.SHOWS -> Res.string.library_shows
                  Tab.SAVED -> Res.string.library_saved
                  Tab.HISTORY -> Res.string.library_history
                },
              ),
            style = AppTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
  }

  @Composable
  private fun Illustration(tab: Tab, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      Image(
        painter =
          painterResource(
            when (tab) {
              Tab.QUEUE -> Res.drawable.library_queue_illustration
              Tab.SHOWS -> Res.drawable.library_shows_illustration
              Tab.SAVED -> Res.drawable.library_illustration
              Tab.HISTORY -> Res.drawable.library_history_illustration
            },
          ),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
      )
    }
  }

  @Composable
  private fun EmptyMessage(
    tab: Tab,
    onDiscoverPodcasts: () -> Unit,
    compact: Boolean,
    large: Boolean,
    horizontal: Boolean,
    modifier: Modifier = Modifier,
  ) {
    val alignment = if (horizontal) Alignment.Start else Alignment.CenterHorizontally
    val textAlign = if (horizontal) TextAlign.Start else TextAlign.Center
    Column(
      modifier =
        modifier.widthIn(max = if (large) 720.dp else 560.dp).testTag("library-empty-message"),
      horizontalAlignment = alignment,
    ) {
      Text(
        text = stringResource(tab.emptyTitle()),
        style =
          when {
            compact -> AppTheme.typography.headlineSmall
            large -> AppTheme.typography.displaySmall
            else -> AppTheme.typography.headlineLarge
          },
        fontWeight = FontWeight.SemiBold,
        textAlign = textAlign,
        color = AppTheme.colorScheme.onBackground,
      )
      Text(
        text = stringResource(tab.emptyDescription()),
        modifier = Modifier.padding(top = if (horizontal) 8.dp else 12.dp),
        style =
          when {
            compact -> AppTheme.typography.bodyMedium
            large -> AppTheme.typography.headlineSmall
            else -> AppTheme.typography.bodyLarge
          },
        textAlign = textAlign,
        color = AppTheme.colorScheme.onSurfaceVariant,
      )
      Button(
        onClick = onDiscoverPodcasts,
        modifier =
          Modifier.padding(top = if (horizontal) 12.dp else 24.dp)
            .widthIn(min = if (compact) 224.dp else 288.dp)
            .heightIn(min = if (compact) 48.dp else 56.dp)
            .testTag("library-discover"),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 12.dp),
      ) {
        Text(
          text = stringResource(Res.string.library_discover),
          style = AppTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
        )
      }
    }
  }

  private fun Tab.emptyTitle(): StringResource =
    when (this) {
      Tab.QUEUE -> Res.string.library_queue_empty_title
      Tab.SHOWS -> Res.string.library_shows_empty_title
      Tab.SAVED -> Res.string.library_saved_empty_title
      Tab.HISTORY -> Res.string.library_history_empty_title
    }

  private fun Tab.emptyDescription(): StringResource =
    when (this) {
      Tab.QUEUE -> Res.string.library_queue_empty_description
      Tab.SHOWS -> Res.string.library_shows_empty_description
      Tab.SAVED -> Res.string.library_saved_empty_description
      Tab.HISTORY -> Res.string.library_history_empty_description
    }
}
