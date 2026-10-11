package software.ralf.storymile.home

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the welcome screen before the user follows a podcast. */
interface HomePresenter<ModelT : BaseModel> : ComposePresenter<Unit, ModelT>
