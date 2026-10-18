package software.ralf.storymile.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import software.ralf.storymile.theme.AppTheme
import software.ralf.storymile.theme.generated.resources.Res
import software.ralf.storymile.theme.generated.resources.clear_search
import software.ralf.storymile.theme.generated.resources.search_podcasts

/**
 * Shows a search input with fixed icon slots and fading placeholder and clear button. When
 * [onClick] is set, shows a navigation button with the same layout and no text input. Use
 * [fieldModifier] to request focus on the editable input.
 */
@Composable
fun SearchField(
  modifier: Modifier = Modifier,
  placeholder: String = stringResource(Res.string.search_podcasts),
  state: TextFieldState = rememberTextFieldState(),
  onClick: (() -> Unit)? = null,
  fieldModifier: Modifier = Modifier,
  placeholderModifier: Modifier = Modifier,
  clearButtonModifier: Modifier = Modifier,
) {
  val focusManager = LocalFocusManager.current
  val shape = RoundedCornerShape(12.dp)
  val iconSlotWidth = 52.dp
  val fadeDurationMillis = 120
  Box(
    modifier
      .clip(shape)
      .then(
        if (onClick == null) {
          Modifier
        } else {
          Modifier.clickable(role = Role.Button, onClick = onClick)
        },
      ),
  ) {
    OutlinedTextField(
      state = state,
      enabled = onClick == null,
      modifier =
        fieldModifier
          .fillMaxWidth()
          .heightIn(min = 52.dp)
          .then(
            if (onClick == null) {
              Modifier.semantics { contentDescription = placeholder }
            } else {
              Modifier.clearAndSetSemantics {}
            },
          ),
      textStyle = AppTheme.typography.bodyLarge,
      contentPadding = PaddingValues(vertical = 12.dp),
      leadingIcon = {
        Box(Modifier.width(iconSlotWidth)) {
          Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.padding(start = 12.dp).testTag("search-icon"),
          )
        }
      },
      trailingIcon = {
        Box(Modifier.width(iconSlotWidth), contentAlignment = Alignment.CenterEnd) {
          AnimatedVisibility(
            visible = onClick == null && state.text.isNotEmpty(),
            enter = fadeIn(tween(fadeDurationMillis)),
            exit = fadeOut(tween(fadeDurationMillis)),
          ) {
            IconButton(onClick = { state.clearText() }, modifier = clearButtonModifier) {
              Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = stringResource(Res.string.clear_search),
              )
            }
          }
        }
      },
      lineLimits = TextFieldLineLimits.SingleLine,
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      onKeyboardAction = { focusManager.clearFocus() },
      shape = shape,
      colors =
        OutlinedTextFieldDefaults.colors(
          unfocusedBorderColor = AppTheme.colorScheme.outlineVariant,
          disabledBorderColor = AppTheme.colorScheme.outlineVariant,
          unfocusedContainerColor = AppTheme.colorScheme.surface,
          focusedContainerColor = AppTheme.colorScheme.surface,
          disabledContainerColor = AppTheme.colorScheme.surface,
          disabledLeadingIconColor = AppTheme.colorScheme.onSurfaceVariant,
        ),
    )
    // Keep the placeholder composed through its fade after text is entered.
    AnimatedVisibility(
      visible = state.text.isEmpty(),
      modifier =
        Modifier.align(Alignment.CenterStart)
          .padding(horizontal = iconSlotWidth)
          .then(placeholderModifier),
      enter = fadeIn(tween(fadeDurationMillis)),
      exit = fadeOut(tween(fadeDurationMillis)),
    ) {
      Text(
        text = placeholder,
        modifier = if (onClick == null) Modifier.clearAndSetSemantics {} else Modifier,
        style = AppTheme.typography.bodyLarge,
        color = AppTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (onClick != null) {
      // The disabled field consumes taps. Keep this target above it and button semantics on the
      // root.
      Box(Modifier.matchParentSize().pointerInput(onClick) { detectTapGestures { onClick() } })
    }
  }
}
