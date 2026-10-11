import StorymileApp
import SwiftUI

class AppDelegate: NSObject, UIApplicationDelegate, RootScopeProvider {

    private let storymileApplication: KmpApplication = KmpApplication()

    var rootScope: Scope {
        get {
            storymileApplication.rootScope
        }
    }

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        storymileApplication.create(
            appGraph: IosAppGraphKt.createIosAppGraph(
                application: application,
                rootScopeProvider: storymileApplication
            )
        )
        return true
    }
}

@main
struct iOSApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeContentView(rootScopeProvider: appDelegate)
        }
    }
}
