package software.ralf.storymile.tabs

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the app's tabs layer. */
interface TabsPresenter : ComposePresenter<Unit, TabsPresenter.Model> {
  /** Destinations available in every adaptive navigation layout. */
  enum class Tab {
    /** The app's starting destination. */
    HOME,

    /** Podcasts in the user's library. */
    LIBRARY,

    /** Episodes available offline. */
    DOWNLOADS,
  }

  /** Navigation and content models for the app's template slots. */
  data class Model(
    /** Destination whose content is currently visible. */
    val selectedTab: Tab,
    /** Content for the selected destination. */
    val content: BaseModel,
    /** Selects a destination without changing playback. */
    val onSelectTab: (Tab) -> Unit,
  ) : BaseModel
}
