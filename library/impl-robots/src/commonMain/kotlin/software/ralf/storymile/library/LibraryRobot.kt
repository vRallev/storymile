package software.ralf.storymile.library

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import dev.zacsweers.metro.AppScope
import software.ralf.app.platform.inject.robot.ContributesRobot
import software.ralf.app.platform.robot.ComposeRobot

@ContributesRobot(AppScope::class)
class LibraryRobot : ComposeRobot() {
  fun seeEmptyLibrary() {
    compose.onNodeWithTag("library").assertIsDisplayed()
    compose.onNodeWithTag("library").onChildren().assertCountEquals(0)
  }
}
