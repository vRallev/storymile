package software.ralf.storymile.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import software.ralf.storymile.library.impl.generated.resources.library_illustration
import software.ralf.storymile.library.impl.generated.resources.library_queue
import software.ralf.storymile.library.impl.generated.resources.library_queue_empty_description
import software.ralf.storymile.library.impl.generated.resources.library_queue_empty_title
import software.ralf.storymile.library.impl.generated.resources.library_saved
import software.ralf.storymile.library.impl.generated.resources.library_saved_empty_description
import software.ralf.storymile.library.impl.generated.resources.library_saved_empty_title
import software.ralf.storymile.library.impl.generated.resources.library_shows
import software.ralf.storymile.library.impl.generated.resources.library_shows_empty_description
import software.ralf.storymile.library.impl.generated.resources.library_shows_empty_title
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
        Column(Modifier.weight(1f).padding(horizontal = sidePadding)) {
          Tabs(model)
          if (horizontal) {
            Row(
              modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.Center,
            ) {
              Illustration(
                tab = model.selectedTab,
                modifier =
                  Modifier.padding(end = 24.dp).size(144.dp).testTag("library-illustration"),
              )
              EmptyMessage(
                model = model,
                compact = true,
                large = false,
                horizontal = true,
                modifier = Modifier.weight(1f, fill = false),
              )
            }
          } else {
            Column(
              modifier =
                Modifier.fillMaxWidth().weight(1f).padding(vertical = if (short) 8.dp else 24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
            ) {
              Illustration(
                tab = model.selectedTab,
                modifier =
                  Modifier.weight(1f, fill = false)
                    .size(if (compact) 164.dp else if (large) 256.dp else 216.dp)
                    .testTag("library-illustration"),
              )
              Spacer(Modifier.height(if (short) 8.dp else 24.dp))
              EmptyMessage(model = model, compact = compact, large = large, horizontal = false)
            }
          }
        }
      }
    }
  }

  @Composable
  private fun Tabs(model: LibraryPresenterImpl.Model) {
    FlowRow(
      modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Tab.entries.forEach { tab ->
        val selected = model.selectedTab == tab
        FilterChip(
          selected = selected,
          onClick = { model.onSelectTab(tab) },
          label = {
            Text(
              text =
                stringResource(
                  when (tab) {
                    Tab.QUEUE -> Res.string.library_queue
                    Tab.SHOWS -> Res.string.library_shows
                    Tab.SAVED -> Res.string.library_saved
                  },
                ),
              style = AppTheme.typography.bodyLarge,
            )
          },
          shape = CircleShape,
          colors =
            FilterChipDefaults.filterChipColors(
              containerColor = AppTheme.colorScheme.background,
              labelColor = AppTheme.colorScheme.primary,
              selectedContainerColor = AppTheme.colorScheme.primary,
              selectedLabelColor = AppTheme.colorScheme.onPrimary,
            ),
          border =
            BorderStroke(
              width = 1.dp,
              color =
                if (selected) AppTheme.colorScheme.primary else AppTheme.colorScheme.outlineVariant,
            ),
          modifier = Modifier.heightIn(min = 48.dp).testTag("library-tab-${tab.name.lowercase()}"),
        )
      }
    }
  }

  @Composable
  private fun Illustration(tab: Tab, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
      when (tab) {
        Tab.QUEUE,
        Tab.SHOWS ->
          Icon(
            imageVector =
              if (tab == Tab.QUEUE) {
                Icons.AutoMirrored.Filled.FormatListBulleted
              } else {
                Icons.Default.Podcasts
              },
            contentDescription = null,
            tint = AppTheme.colorScheme.secondary,
            modifier = Modifier.fillMaxSize(0.65f),
          )
        Tab.SAVED ->
          Image(
            painter = painterResource(Res.drawable.library_illustration),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
          )
      }
    }
  }

  @Composable
  private fun EmptyMessage(
    model: LibraryPresenterImpl.Model,
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
        text = stringResource(model.selectedTab.emptyTitle()),
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
        text = stringResource(model.selectedTab.emptyDescription()),
        modifier = Modifier.padding(top = 12.dp),
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
        onClick = model.onDiscoverPodcasts,
        modifier =
          Modifier.padding(top = 24.dp)
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
    }

  private fun Tab.emptyDescription(): StringResource =
    when (this) {
      Tab.QUEUE -> Res.string.library_queue_empty_description
      Tab.SHOWS -> Res.string.library_shows_empty_description
      Tab.SAVED -> Res.string.library_saved_empty_description
    }
}
