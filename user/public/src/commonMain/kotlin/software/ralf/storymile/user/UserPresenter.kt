package software.ralf.storymile.user

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the active user's navigation and playback. Child state resets when storage changes. */
interface UserPresenter<ModelT : BaseModel> : ComposePresenter<Unit, ModelT>
