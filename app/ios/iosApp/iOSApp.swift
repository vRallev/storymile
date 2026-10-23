import StorymileApp
import SwiftUI

class AppDelegate: NSObject, UIApplicationDelegate {

    private(set) var startup: ApplicationStartup!

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        startup = IosAppGraphKt.createIosApplicationStartup(application: application)
        return true
    }
}

@main
struct iOSApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeContentView(startup: appDelegate.startup)
        }
    }
}
