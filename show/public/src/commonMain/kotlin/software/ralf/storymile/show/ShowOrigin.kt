package software.ralf.storymile.show

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.show.generated.resources.Res
import software.ralf.storymile.show.generated.resources.discover_podcasts
import software.ralf.storymile.show.generated.resources.followed_shows
import software.ralf.storymile.show.generated.resources.search_results
import software.ralf.storymile.show.generated.resources.topic_culture
import software.ralf.storymile.show.generated.resources.topic_nature
import software.ralf.storymile.show.generated.resources.topic_science
import software.ralf.storymile.show.generated.resources.topic_technology

/** Identifies the screen that opened a show and scopes its shared elements to that screen. */
enum class ShowOrigin {
  /** Featured shows in Discover. */
  Browse,
  /** Search results in Discover. */
  Search,
  /** Results for the Nature topic. */
  Nature,
  /** Results for the Science topic. */
  Science,
  /** Results for the Culture topic. */
  Culture,
  /** Results for the Technology topic. */
  Technology,
  /** Followed shows in Library. */
  Library;

  /** Returns the localized name of this source screen. */
  @Composable
  fun title(): String =
    stringResource(
      when (this) {
        Browse -> Res.string.discover_podcasts
        Search -> Res.string.search_results
        Nature -> Res.string.topic_nature
        Science -> Res.string.topic_science
        Culture -> Res.string.topic_culture
        Technology -> Res.string.topic_technology
        Library -> Res.string.followed_shows
      },
    )
}
