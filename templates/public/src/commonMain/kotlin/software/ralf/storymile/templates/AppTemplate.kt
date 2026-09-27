package software.ralf.storymile.templates

import software.ralf.app.platform.presenter.BaseModel
import software.ralf.app.platform.presenter.template.Template
import software.ralf.app.platform.presenter.template.toTemplate

/** Application-level templates selected by the presenter tree. */
sealed interface AppTemplate : Template {
  /**
   * Application slots shared by compact and expanded layouts.
   *
   * The template renderer places these slots; each child renderer adapts its own content.
   */
  data class AdaptiveTemplate(
    /** Main feature content, using all available space when other slots are absent. */
    val content: BaseModel,
    /**
     * App tabs. The renderer reads [LocalTabPlacement] and provides its height at the bottom or
     * width at the start. The template fills the other dimension.
     */
    val tabs: BaseModel? = null,
    /**
     * Persistent playback above bottom tabs or across the expanded bottom edge. Its renderer
     * provides the height; the template fills the width.
     */
    val playback: BaseModel? = null,
    /**
     * Foreground layer above all other slots. Its renderer owns modality, scrims, focus, dismissal,
     * and back handling.
     */
    val overlay: BaseModel? = null,
  ) : AppTemplate
}

/** Wraps any feature model in the appropriate [AppTemplate]. */
fun BaseModel.toAppTemplate(): AppTemplate {
  return toTemplate<AppTemplate> { AppTemplate.AdaptiveTemplate(content = it) }
}
