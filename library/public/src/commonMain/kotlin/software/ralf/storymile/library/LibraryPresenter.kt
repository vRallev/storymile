package software.ralf.storymile.library

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the user's library and its discovery navigation stack for the app's content slot. */
interface LibraryPresenter<ModelT : BaseModel> : ComposePresenter<Unit, ModelT>
