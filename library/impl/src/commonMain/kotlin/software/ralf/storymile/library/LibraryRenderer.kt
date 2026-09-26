package software.ralf.storymile.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.library.`impl`.generated.resources.Res
import software.ralf.storymile.library.`impl`.generated.resources.library_empty
import software.ralf.storymile.library.`impl`.generated.resources.library_title
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class LibraryRenderer : ComposeRenderer<LibraryPresenterImpl.Model>() {
  @Composable
  override fun Compose(model: LibraryPresenterImpl.Model, modifier: Modifier) {
    Column(
      modifier = modifier.fillMaxSize().padding(24.dp).testTag("library"),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
      Text(stringResource(Res.string.library_title), style = AppTheme.typography.headlineMedium)
      Text(
        text = stringResource(Res.string.library_empty),
        modifier = Modifier.testTag("libraryEmpty"),
        style = AppTheme.typography.bodyLarge,
      )
    }
  }
}
