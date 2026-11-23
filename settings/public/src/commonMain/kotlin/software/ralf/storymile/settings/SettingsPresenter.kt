package software.ralf.storymile.settings

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter

/** Presents the account and preferences for the active session. */
interface SettingsPresenter : ComposePresenter<Unit, SettingsPresenter.Model> {
  /** Account state shown on the Settings screen. */
  sealed interface Account {
    /** Session without a signed-in account. */
    data object Guest : Account
  }

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
    val account: Account,
    /** Section displayed in the detail pane. Starts at [Section.PLAYBACK]. */
    val selectedSection: Section,
    /** Selects a section in the detail pane. */
    val onSelectSection: (Section) -> Unit,
    /** Returns to the screen that opened Settings. */
    val onBack: () -> Unit,
  ) : BaseModel
}
