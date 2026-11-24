package software.ralf.storymile.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
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
  val shape = RoundedCornerShape(12.dp)
  val iconSlotWidth = 52.dp
  val fadeDurationMillis = 120
  val field = fieldModifier.fillMaxWidth().heightIn(min = 52.dp)
  val textStyle = AppTheme.typography.bodyLarge
  val contentPadding = PaddingValues(vertical = 12.dp)
  val colors =
    OutlinedTextFieldDefaults.colors(
      unfocusedBorderColor = AppTheme.colorScheme.outlineVariant,
      disabledBorderColor = AppTheme.colorScheme.outlineVariant,
      unfocusedContainerColor = AppTheme.colorScheme.surface,
      focusedContainerColor = AppTheme.colorScheme.surface,
      disabledContainerColor = AppTheme.colorScheme.surface,
      disabledLeadingIconColor = AppTheme.colorScheme.onSurfaceVariant,
    )
  val searchIcon: @Composable () -> Unit = {
    Box(Modifier.width(iconSlotWidth)) {
      Icon(
        imageVector = Icons.Outlined.Search,
        contentDescription = null,
        modifier = Modifier.padding(start = 12.dp).testTag("search-icon"),
      )
    }
  }
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
    if (onClick == null) {
      val focusManager = LocalFocusManager.current
      OutlinedTextField(
        state = state,
        modifier = field.semantics { contentDescription = placeholder },
        textStyle = textStyle,
        contentPadding = contentPadding,
        leadingIcon = searchIcon,
        trailingIcon = {
          Box(Modifier.width(iconSlotWidth), contentAlignment = Alignment.CenterEnd) {
            AnimatedVisibility(
              visible = state.text.isNotEmpty(),
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
        colors = colors,
      )
    } else {
      val interactionSource = remember { MutableInteractionSource() }
      Box(
        modifier =
          field
            .clearAndSetSemantics {}
            .defaultMinSize(
              minWidth = OutlinedTextFieldDefaults.MinWidth,
              minHeight = OutlinedTextFieldDefaults.MinHeight,
            ),
        propagateMinConstraints = true,
      ) {
        OutlinedTextFieldDefaults.decorator(
            state = state,
            enabled = false,
            lineLimits = TextFieldLineLimits.SingleLine,
            outputTransformation = null,
            interactionSource = interactionSource,
            leadingIcon = searchIcon,
            trailingIcon = { Box(Modifier.width(iconSlotWidth)) },
            colors = colors,
            contentPadding = contentPadding,
            container = {
              OutlinedTextFieldDefaults.Container(
                enabled = false,
                isError = false,
                interactionSource = interactionSource,
                colors = colors,
                shape = shape,
              )
            },
          )
          .Decoration {
            val text =
              state.text
                .toString()
                .replace(oldChar = '\n', newChar = ' ')
                .replace(oldChar = '\r', newChar = '\uFEFF')
            val measurer = rememberTextMeasurer()
            val layout =
              measurer.measure(
                text = text,
                style = textStyle,
                softWrap = false,
                maxLines = 1,
              )
            val minimumHeight =
              with(LocalDensity.current) { ceil(layout.getLineBottom(0)).toInt().toDp() }
            BasicText(
              text = text,
              modifier = Modifier.heightIn(min = minimumHeight),
              style =
                textStyle.copy(color = textStyle.color.takeOrElse { colors.disabledTextColor }),
              softWrap = false,
              maxLines = 1,
            )
          }
      }
    }
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
  }
}
