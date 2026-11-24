package software.ralf.storymile.settings

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.login.Login
import software.ralf.storymile.theme.ThemeMode

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
    val account: Login.State,
    /** Starts login or signs out the current account. */
    val onAccountAction: () -> Unit,
    /** Section displayed in the detail pane. Starts at [Section.PLAYBACK]. */
    val selectedSection: Section,
    /** The current app appearance preference. */
    val themeMode: ThemeMode,
    /** Selects a section in the detail pane. */
    val onSelectSection: (Section) -> Unit,
    /** Applies an appearance preference to the app. */
    val onSelectThemeMode: (ThemeMode) -> Unit,
    /** Returns to the screen that opened Settings. */
    val onBack: () -> Unit,
  ) : BaseModel
}
