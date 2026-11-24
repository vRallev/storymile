import GoogleSignIn
@preconcurrency import StorymileApp
import UIKit

@MainActor
final class GoogleSignInPresenter: NSObject, @preconcurrency IosGoogleSignInHost {
    nonisolated private let clientID = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String

    nonisolated var configured: Bool {
        guard let prefix = clientID?.prefix(while: { $0 != "." }) else { return false }
        return !prefix.isEmpty && prefix != "unconfigured"
    }

    static func handle(_ url: URL) {
        GIDSignIn.sharedInstance.handle(url)
    }

    func signIn(serverClientId: String, completion: @escaping (String?) -> Void, failed: @escaping () -> Void) {
        guard configured, let clientID,
              let scene = UIApplication.shared.connectedScenes
                  .compactMap({ $0 as? UIWindowScene })
                  .first(where: { $0.activationState == .foregroundActive }),
              var presenter = scene.keyWindow?.rootViewController else {
            failed()
            return
        }
        while let presented = presenter.presentedViewController {
            presenter = presented
        }

        let google = GIDSignIn.sharedInstance
        google.configuration = GIDConfiguration(clientID: clientID, serverClientID: serverClientId)
        google.signIn(withPresenting: presenter) { response, error in
            if let error = error as NSError? {
                if error.domain == kGIDSignInErrorDomain && error.code == GIDSignInError.canceled.rawValue {
                    completion(nil)
                } else {
                    failed()
                }
            } else if let token = response?.user.idToken?.tokenString {
                completion(token)
            } else {
                failed()
            }
        }
    }

    func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }
}
