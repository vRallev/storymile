package software.ralf.storymile.library

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the podcast library as a model for the library renderer. */
interface LibraryPresenter : ComposePresenter<Unit, BaseModel>
