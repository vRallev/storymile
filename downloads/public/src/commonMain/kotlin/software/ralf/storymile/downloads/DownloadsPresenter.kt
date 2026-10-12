package software.ralf.storymile.downloads

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents episodes available for offline listening. */
interface DownloadsPresenter<ModelT : BaseModel> : ComposePresenter<Unit, ModelT>
