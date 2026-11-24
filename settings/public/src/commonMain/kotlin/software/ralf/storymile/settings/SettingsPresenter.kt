package software.ralf.storymile.settings

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.user.GoogleLogin

/** Presents the account and preferences for the active session. */
interface SettingsPresenter : ComposePresenter<Unit, SettingsPresenter.Model> {
  /** Preference sections available in the detail pane. */
  enum class Section {
    /** Speed, silence, and resume behavior. */
    PLAYBACK,
    /** Offline listening and storage. */
    DOWNLOADS,
    /** Display preferences. */
    APPEARANCE,
    /** Notification preferences. */
    NOTIFICATIONS,
    /** Privacy preferences. */
    PRIVACY,
  }

  /** State and actions for the Settings overview. */
  data class Model(
    /** Account state for the active session. */
    val account: GoogleLogin.State,
    /** Starts Google sign-in on this device. */
    val onSignIn: () -> Unit,
    /** Returns to the local guest session. */
    val onSignOut: () -> Unit,
    /** Section displayed in the detail pane. Starts at [Section.PLAYBACK]. */
    val selectedSection: Section,
    /** Selects a section in the detail pane. */
    val onSelectSection: (Section) -> Unit,
    /** Returns to the screen that opened Settings. */
    val onBack: () -> Unit,
  ) : BaseModel
}
