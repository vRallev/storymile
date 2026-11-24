import CryptoKit
import GoogleSignIn
@preconcurrency import StorymileApp
import UIKit

@MainActor
final class GoogleSignInPresenter: NSObject, @preconcurrency IosGoogleSignIn {
    private let clientID = Bundle.main.object(forInfoDictionaryKey: "GIDClientID") as? String

    var configured: Bool {
        guard let prefix = clientID?.prefix(while: { $0 != "." }) else { return false }
        return !prefix.isEmpty && prefix != "unconfigured"
    }

    func signIn(serverClientId: String, result: IosGoogleSignInResult) {
        guard configured, let clientID,
              let scene = UIApplication.shared.connectedScenes
                  .compactMap({ $0 as? UIWindowScene })
                  .first(where: { $0.activationState == .foregroundActive }),
              var presenter = scene.keyWindow?.rootViewController else {
            result.failed()
            return
        }
        while let presented = presenter.presentedViewController {
            presenter = presented
        }

        let nonce = UUID().uuidString
        let digest = SHA256.hash(data: Data(nonce.utf8)).map { String(format: "%02x", $0) }.joined()
        let google = GIDSignIn.sharedInstance
        google.configuration = GIDConfiguration(clientID: clientID, serverClientID: serverClientId)
        google.signIn(withPresenting: presenter, hint: nil, additionalScopes: nil, nonce: digest) { response, error in
            if let error = error as NSError? {
                if error.domain == kGIDSignInErrorDomain && error.code == GIDSignInError.canceled.rawValue {
                    result.cancelled()
                } else {
                    result.failed()
                }
                return
            }
            guard let user = response?.user, let idToken = user.idToken?.tokenString else {
                result.failed()
                return
            }
            result.signedIn(idToken: idToken, accessToken: user.accessToken.tokenString, nonce: nonce)
        }
    }

    func signOut() {
        GIDSignIn.sharedInstance.signOut()
    }
}
