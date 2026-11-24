package software.ralf.storymile.settings

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.compose.ComposePresenter
import software.ralf.storymile.theme.ThemeMode

/** Presents the account and preferences for the active session. */
interface SettingsPresenter : ComposePresenter<Unit, SettingsPresenter.Model> {
  /** Account state shown on the Settings screen. */
  sealed interface Account {
    /** Session without a signed-in account. */
    data object Guest : Account

    /** Fake signed-in account on the Basic tier. */
    data object Fake : Account
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

  /** Choices for the local files when signing out of the fake account. */
  data class SignOutConfirmation(
    /** Signs out and keeps local files. */
    val onKeepFiles: () -> Unit,
    /** Signs out and deletes local files while preserving preferences. */
    val onWipeFiles: () -> Unit,
    /** Dismisses the confirmation. */
    val onCancel: () -> Unit,
  )

  /** State and actions for the Settings overview. */
  data class Model(
    /** Account state for the active session. */
    val account: Account,
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
    /** Signs in as a fake user or asks how to handle local files on sign-out. */
    val onAccountLongPress: (() -> Unit)? = null,
    /** The pending sign-out choice, when shown. */
    val signOutConfirmation: SignOutConfirmation? = null,
    /** Whether the last account action failed. */
    val accountActionFailed: Boolean = false,
  ) : BaseModel
}
