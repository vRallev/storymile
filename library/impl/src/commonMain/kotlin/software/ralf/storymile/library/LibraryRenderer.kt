package software.ralf.storymile.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.library.impl.generated.resources.Res
import software.ralf.storymile.library.impl.generated.resources.library_discover
import software.ralf.storymile.library.impl.generated.resources.library_empty_description
import software.ralf.storymile.library.impl.generated.resources.library_empty_title
import software.ralf.storymile.library.impl.generated.resources.library_episodes
import software.ralf.storymile.library.impl.generated.resources.library_illustration
import software.ralf.storymile.library.impl.generated.resources.library_shows
import software.ralf.storymile.library.impl.generated.resources.library_title
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
      val sidePadding = if (compact) 16.dp else 32.dp
      Column(Modifier.fillMaxSize().padding(horizontal = sidePadding)) {
        Header(model, compact)
        if (horizontal) {
          Row(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
          ) {
            Image(
              painter = painterResource(Res.drawable.library_illustration),
              contentDescription = null,
              modifier = Modifier.padding(end = 24.dp).size(144.dp).testTag("library-illustration"),
            )
            EmptyMessage(
              model,
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
            Image(
              painter = painterResource(Res.drawable.library_illustration),
              contentDescription = null,
              modifier =
                Modifier.weight(1f, fill = false)
                  .size(if (compact) 164.dp else if (large) 256.dp else 216.dp)
                  .testTag("library-illustration"),
            )
            Spacer(Modifier.height(if (short) 8.dp else 24.dp))
            EmptyMessage(model, compact, large, horizontal = false)
          }
        }
      }
    }
  }

  @Composable
  private fun Header(model: LibraryPresenterImpl.Model, compact: Boolean) {
    Text(
      stringResource(Res.string.library_title),
      modifier = Modifier.padding(top = 16.dp).semantics { heading() },
      style = if (compact) AppTheme.typography.headlineLarge else AppTheme.typography.displaySmall,
      fontWeight = FontWeight.Bold,
      color = AppTheme.colorScheme.onBackground,
    )
    FlowRow(
      Modifier.padding(top = 8.dp, bottom = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      LibraryPresenterImpl.Filter.entries.forEach { filter ->
        val selected = model.selectedFilter == filter
        FilterChip(
          selected = selected,
          onClick = { model.onSelectFilter(filter) },
          label = {
            Text(
              stringResource(
                if (filter == LibraryPresenterImpl.Filter.EPISODES) Res.string.library_episodes
                else Res.string.library_shows,
              ),
              style = AppTheme.typography.bodyLarge,
              modifier = Modifier.padding(horizontal = if (selected) 8.dp else 16.dp),
            )
          },
          leadingIcon =
            if (selected) {
              {
                Icon(
                  Icons.Default.Check,
                  contentDescription = null,
                  modifier = Modifier.size(20.dp),
                )
              }
            } else null,
          shape = CircleShape,
          colors =
            FilterChipDefaults.filterChipColors(
              containerColor = AppTheme.colorScheme.background,
              labelColor = AppTheme.colorScheme.primary,
              selectedContainerColor = AppTheme.colorScheme.primary,
              selectedLabelColor = AppTheme.colorScheme.onPrimary,
              selectedLeadingIconColor = AppTheme.colorScheme.onPrimary,
            ),
          border =
            BorderStroke(
              1.dp,
              if (selected) AppTheme.colorScheme.primary else AppTheme.colorScheme.outlineVariant,
            ),
          modifier =
            Modifier.heightIn(min = 48.dp).testTag("library-filter-${filter.name.lowercase()}"),
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
      modifier.widthIn(max = if (large) 720.dp else 560.dp).testTag("library-empty-message"),
      horizontalAlignment = alignment,
    ) {
      Text(
        stringResource(Res.string.library_empty_title),
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
        stringResource(Res.string.library_empty_description),
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
          stringResource(Res.string.library_discover),
          style = AppTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
