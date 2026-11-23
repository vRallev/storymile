package software.ralf.storymile.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SdStorage
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.app.platform.inject.ContributesRenderer
import software.ralf.app.platform.renderer.ComposeRenderer
import software.ralf.storymile.design.PaneDivider
import software.ralf.storymile.design.TitleBar
import software.ralf.storymile.settings.SettingsPresenter.Section
import software.ralf.storymile.settings.impl.generated.resources.Res
import software.ralf.storymile.settings.impl.generated.resources.account
import software.ralf.storymile.settings.impl.generated.resources.appearance
import software.ralf.storymile.settings.impl.generated.resources.default_speed
import software.ralf.storymile.settings.impl.generated.resources.default_speed_description
import software.ralf.storymile.settings.impl.generated.resources.default_speed_value
import software.ralf.storymile.settings.impl.generated.resources.downloads
import software.ralf.storymile.settings.impl.generated.resources.guest
import software.ralf.storymile.settings.impl.generated.resources.manage_storage
import software.ralf.storymile.settings.impl.generated.resources.manage_storage_value
import software.ralf.storymile.settings.impl.generated.resources.no_settings_available
import software.ralf.storymile.settings.impl.generated.resources.notifications
import software.ralf.storymile.settings.impl.generated.resources.playback
import software.ralf.storymile.settings.impl.generated.resources.privacy
import software.ralf.storymile.settings.impl.generated.resources.rewind_on_resume
import software.ralf.storymile.settings.impl.generated.resources.rewind_on_resume_description
import software.ralf.storymile.settings.impl.generated.resources.rewind_on_resume_value
import software.ralf.storymile.settings.impl.generated.resources.settings
import software.ralf.storymile.settings.impl.generated.resources.sign_in_to_sync
import software.ralf.storymile.settings.impl.generated.resources.skip_silence
import software.ralf.storymile.settings.impl.generated.resources.skip_silence_description
import software.ralf.storymile.settings.impl.generated.resources.theme
import software.ralf.storymile.settings.impl.generated.resources.theme_value
import software.ralf.storymile.settings.impl.generated.resources.wifi_only
import software.ralf.storymile.theme.AppTheme

@ContributesRenderer
class SettingsRenderer : ComposeRenderer<SettingsPresenter.Model>() {
  @Composable
  override fun Compose(model: SettingsPresenter.Model, modifier: Modifier) {
    val overviewScroll = rememberScrollState()
    val sectionsScroll = rememberScrollState()
    val detailScroll = key(model.selectedSection) { rememberScrollState() }
    BoxWithConstraints(modifier.fillMaxSize().testTag("settings-screen")) {
      val wide = maxWidth / LocalDensity.current.fontScale >= 760.dp
      val sidePadding = if (maxWidth < 600.dp) 20.dp else 32.dp
      Column(Modifier.fillMaxSize()) {
        TitleBar(title = stringResource(Res.string.settings), onBack = model.onBack)
        if (wide) {
          Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = sidePadding),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
          ) {
            Column(
              modifier =
                Modifier.weight(0.85f)
                  .fillMaxHeight()
                  .verticalScroll(sectionsScroll)
                  .testTag("settings-sections")
                  .padding(vertical = 16.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              Account(model.account)
              Section.entries.forEach { section ->
                SectionRow(section = section, selected = model.selectedSection == section) {
                  model.onSelectSection(section)
                }
              }
            }
            PaneDivider()
            Column(
              Modifier.weight(1.35f)
                .fillMaxHeight()
                .verticalScroll(detailScroll)
                .testTag("settings-detail")
                .padding(vertical = 16.dp),
            ) {
              when (model.selectedSection) {
                Section.PLAYBACK -> Playback(detailed = true)
                Section.DOWNLOADS -> Downloads()
                Section.APPEARANCE -> Appearance()
                Section.NOTIFICATIONS -> EmptySection(stringResource(Res.string.notifications))
                Section.PRIVACY -> EmptySection(stringResource(Res.string.privacy))
              }
            }
          }
        } else {
          Column(
            modifier =
              Modifier.fillMaxSize()
                .verticalScroll(overviewScroll)
                .testTag("settings-overview")
                .padding(horizontal = sidePadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Account(model.account)
            Playback(detailed = false)
            Downloads()
            Appearance()
            Group {
              SectionRow(Section.NOTIFICATIONS)
              Divider()
              SectionRow(Section.PRIVACY)
            }
          }
        }
      }
    }
  }

  @Composable
  private fun Account(account: SettingsPresenter.Account) {
    Group(title = stringResource(Res.string.account)) {
      when (account) {
        SettingsPresenter.Account.Guest ->
          SettingRow(
            title = stringResource(Res.string.guest),
            icon = Icons.Filled.AccountCircle,
            value = stringResource(Res.string.sign_in_to_sync),
          )
      }
    }
  }

  @Composable
  private fun Playback(detailed: Boolean) {
    Group(title = stringResource(Res.string.playback)) {
      SettingRow(
        title = stringResource(Res.string.default_speed),
        icon = Icons.Outlined.Speed,
        description = if (detailed) stringResource(Res.string.default_speed_description) else null,
        value = stringResource(Res.string.default_speed_value),
      )
      Divider()
      SettingRow(
        title = stringResource(Res.string.skip_silence),
        icon = Icons.Outlined.GraphicEq,
        modifier = Modifier.testTag("settings-skip-silence"),
        description = if (detailed) stringResource(Res.string.skip_silence_description) else null,
        checked = true,
      )
      if (detailed) {
        Divider()
        SettingRow(
          title = stringResource(Res.string.rewind_on_resume),
          icon = Icons.Outlined.History,
          description = stringResource(Res.string.rewind_on_resume_description),
          value = stringResource(Res.string.rewind_on_resume_value),
        )
      }
    }
  }

  @Composable
  private fun Downloads() {
    Group(title = stringResource(Res.string.downloads)) {
      SettingRow(
        title = stringResource(Res.string.wifi_only),
        icon = Icons.Outlined.Wifi,
        checked = true,
      )
      Divider()
      SettingRow(
        title = stringResource(Res.string.manage_storage),
        icon = Icons.Outlined.SdStorage,
        value = stringResource(Res.string.manage_storage_value),
      )
    }
  }

  @Composable
  private fun Appearance() {
    Group(title = stringResource(Res.string.appearance)) {
      SettingRow(
        title = stringResource(Res.string.theme),
        icon = Icons.Outlined.Palette,
        value = stringResource(Res.string.theme_value),
      )
    }
  }

  @Composable
  private fun EmptySection(title: String) {
    Group(title = title) {
      Text(
        text = stringResource(Res.string.no_settings_available),
        modifier = Modifier.padding(16.dp),
        style = AppTheme.typography.bodyMedium,
      )
    }
  }

  @Composable
  private fun SectionRow(section: Section, selected: Boolean? = null, onClick: () -> Unit = {}) {
    val (title, icon) =
      when (section) {
        Section.PLAYBACK -> Res.string.playback to Icons.Outlined.Speed
        Section.DOWNLOADS -> Res.string.downloads to Icons.Outlined.Download
        Section.APPEARANCE -> Res.string.appearance to Icons.Outlined.Palette
        Section.NOTIFICATIONS -> Res.string.notifications to Icons.Outlined.Notifications
        Section.PRIVACY -> Res.string.privacy to Icons.Outlined.GppGood
      }
    SettingRow(title = stringResource(title), icon = icon, selected = selected, onClick = onClick)
  }

  @Composable
  private fun Group(title: String? = null, content: @Composable () -> Unit) {
    OutlinedCard(
      modifier = Modifier.fillMaxWidth(),
      colors =
        CardDefaults.outlinedCardColors(containerColor = AppTheme.colorScheme.surfaceContainer),
      border = BorderStroke(1.dp, AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
      if (title != null) {
        Text(
          text = title,
          modifier =
            Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp).semantics { heading() },
          style = AppTheme.typography.titleMedium,
        )
      }
      content()
    }
  }

  @Composable
  private fun Divider() {
    HorizontalDivider(
      modifier = Modifier.padding(horizontal = 16.dp),
      color = AppTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
  }

  @Composable
  private fun SettingRow(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    description: String? = null,
    value: String? = null,
    checked: Boolean? = null,
    selected: Boolean? = null,
    onClick: () -> Unit = {},
  ) {
    val action =
      when {
        selected != null ->
          Modifier.selectable(selected = selected, role = Role.Tab, onClick = onClick)
        checked != null ->
          Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = {})
        else -> Modifier.clickable(role = Role.Button, onClick = onClick)
      }
    ListItem(
      modifier = modifier.clip(AppTheme.shapes.large).then(action),
      headlineContent = { Text(title) },
      supportingContent = description?.let { { Text(it) } },
      leadingContent = { Icon(imageVector = icon, contentDescription = null) },
      trailingContent = {
        if (checked == null) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            if (value != null) {
              Text(text = value, style = AppTheme.typography.bodyMedium)
            }
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              tint = AppTheme.colorScheme.primary,
            )
          }
        } else {
          Switch(checked = checked, onCheckedChange = null)
        }
      },
      colors =
        ListItemDefaults.colors(
          containerColor =
            if (selected == true) AppTheme.colorScheme.secondaryContainer else Color.Transparent,
          leadingIconColor = AppTheme.colorScheme.primary,
        ),
    )
  }
}
