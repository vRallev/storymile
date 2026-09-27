package software.ralf.storymile.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Material 3 palettes for the application's light and dark appearances. */
object StorymileColorSchemes {
  /**
   * Paper Tide: warm paper, muted ocean blue, and sand accents.
   *
   * Use [ColorScheme.primaryContainer] for mist-blue selections and [ColorScheme.tertiaryContainer]
   * for sand accents. [ColorScheme.tertiary] is a darker sand tone for text and controls.
   */
  val paperTide: ColorScheme =
    lightColorScheme(
      primary = Color(0xFF365F7D),
      onPrimary = Color(0xFFFFFDF8),
      primaryContainer = Color(0xFFDCE8EE),
      onPrimaryContainer = Color(0xFF233B50),
      inversePrimary = Color(0xFFACC9DC),
      secondary = Color(0xFF5B6E79),
      onSecondary = Color(0xFFFFFDF8),
      secondaryContainer = Color(0xFFDCE3E7),
      onSecondaryContainer = Color(0xFF233B50),
      tertiary = Color(0xFF765832),
      onTertiary = Color(0xFFFFFDF8),
      tertiaryContainer = Color(0xFFD6BA8E),
      onTertiaryContainer = Color(0xFF233B50),
      background = Color(0xFFF7F3EB),
      onBackground = Color(0xFF233B50),
      surface = Color(0xFFFFFDF8),
      onSurface = Color(0xFF233B50),
      surfaceVariant = Color(0xFFE5E9E8),
      onSurfaceVariant = Color(0xFF4B5D68),
      surfaceTint = Color(0xFF365F7D),
      inverseSurface = Color(0xFF182F40),
      inverseOnSurface = Color(0xFFF4EFE5),
      error = Color(0xFFBA1A1A),
      onError = Color(0xFFFFFDF8),
      errorContainer = Color(0xFFFFDAD6),
      onErrorContainer = Color(0xFF410002),
      outline = Color(0xFF71818A),
      outlineVariant = Color(0xFFC4CDCF),
      scrim = Color(0xFF000000),
      surfaceBright = Color(0xFFFFFDF8),
      surfaceContainer = Color(0xFFF1EDE5),
      surfaceContainerHigh = Color(0xFFEBE7DF),
      surfaceContainerHighest = Color(0xFFE5E1D9),
      surfaceContainerLow = Color(0xFFF7F3EB),
      surfaceContainerLowest = Color(0xFFFFFFFF),
      surfaceDim = Color(0xFFDDD9D1),
      primaryFixed = Color(0xFFDCE8EE),
      primaryFixedDim = Color(0xFFACC9DC),
      onPrimaryFixed = Color(0xFF0F202D),
      onPrimaryFixedVariant = Color(0xFF294C63),
      secondaryFixed = Color(0xFFDCE3E7),
      secondaryFixedDim = Color(0xFFAFBFCA),
      onSecondaryFixed = Color(0xFF1F333F),
      onSecondaryFixedVariant = Color(0xFF354B59),
      tertiaryFixed = Color(0xFFE4C79D),
      tertiaryFixedDim = Color(0xFFD6BA8E),
      onTertiaryFixed = Color(0xFF0F202D),
      onTertiaryFixedVariant = Color(0xFF59462D),
    )

  /**
   * Harbor Night: deep navy, ice blue, and champagne accents.
   *
   * Use [ColorScheme.primaryContainer] for blue selections and [ColorScheme.tertiary] for champagne
   * accents.
   */
  val harborNight: ColorScheme =
    darkColorScheme(
      primary = Color(0xFFACC9DC),
      onPrimary = Color(0xFF0F202D),
      primaryContainer = Color(0xFF294C63),
      onPrimaryContainer = Color(0xFFF4EFE5),
      inversePrimary = Color(0xFF365F7D),
      secondary = Color(0xFFAFBFCA),
      onSecondary = Color(0xFF0F202D),
      secondaryContainer = Color(0xFF354B59),
      onSecondaryContainer = Color(0xFFF4EFE5),
      tertiary = Color(0xFFE4C79D),
      onTertiary = Color(0xFF0F202D),
      tertiaryContainer = Color(0xFF59462D),
      onTertiaryContainer = Color(0xFFF4EFE5),
      background = Color(0xFF0F202D),
      onBackground = Color(0xFFF4EFE5),
      surface = Color(0xFF182F40),
      onSurface = Color(0xFFF4EFE5),
      surfaceVariant = Color(0xFF354B59),
      onSurfaceVariant = Color(0xFFAFBFCA),
      surfaceTint = Color(0xFFACC9DC),
      inverseSurface = Color(0xFFF7F3EB),
      inverseOnSurface = Color(0xFF233B50),
      error = Color(0xFFFFB4AB),
      onError = Color(0xFF690005),
      errorContainer = Color(0xFF93000A),
      onErrorContainer = Color(0xFFFFDAD6),
      outline = Color(0xFF899CA9),
      outlineVariant = Color(0xFF405664),
      scrim = Color(0xFF000000),
      surfaceBright = Color(0xFF344B5B),
      surfaceContainer = Color(0xFF182F40),
      surfaceContainerHigh = Color(0xFF203748),
      surfaceContainerHighest = Color(0xFF293F50),
      surfaceContainerLow = Color(0xFF132635),
      surfaceContainerLowest = Color(0xFF0A1924),
      surfaceDim = Color(0xFF0F202D),
      primaryFixed = Color(0xFFDCE8EE),
      primaryFixedDim = Color(0xFFACC9DC),
      onPrimaryFixed = Color(0xFF0F202D),
      onPrimaryFixedVariant = Color(0xFF294C63),
      secondaryFixed = Color(0xFFDCE3E7),
      secondaryFixedDim = Color(0xFFAFBFCA),
      onSecondaryFixed = Color(0xFF1F333F),
      onSecondaryFixedVariant = Color(0xFF354B59),
      tertiaryFixed = Color(0xFFE4C79D),
      tertiaryFixedDim = Color(0xFFD6BA8E),
      onTertiaryFixed = Color(0xFF0F202D),
      onTertiaryFixedVariant = Color(0xFF59462D),
    )
}
