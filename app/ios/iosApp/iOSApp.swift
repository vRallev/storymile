import StorymileApp
import SwiftUI

class AppDelegate: NSObject, UIApplicationDelegate {

    private(set) var kmpApplication: KmpApplication!

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        kmpApplication = IosAppGraphKt.createIosApplication(application: application)
        return true
    }
}

@main
struct iOSApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeContentView(kmpApplication: appDelegate.kmpApplication)
        }
    }
}
