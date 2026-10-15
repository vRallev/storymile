package software.ralf.storymile.discovery

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents catalog browsing and search within the current presenter backstack. */
interface DiscoveryPresenter<ModelT : BaseModel> : ComposePresenter<Unit, ModelT> {
  /** Creates a new discovery destination. */
  interface Factory {
    /** Opens browsing, optionally focusing the search field. */
    fun create(focusSearch: Boolean = false): DiscoveryPresenter<*>
  }
}
