package software.ralf.storymile.login

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/** Presents Google's account chooser from the active UIKit scene. */
@OptIn(ExperimentalObjCName::class)
@ObjCName(name = "StorymileIosGoogleSignInHost", swiftName = "IosGoogleSignInHost", exact = true)
interface IosGoogleSignInHost {
  val configured: Boolean

  /** Calls [completion] with null if the user cancels. */
  fun signIn(serverClientId: String, completion: (String?) -> Unit, failed: () -> Unit)

  fun signOut()
}
