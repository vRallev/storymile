import StorymileApp
import SwiftUI

class AppDelegate: NSObject, UIApplicationDelegate {

    private(set) var storymileApplication: IosApplication!

    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        storymileApplication = IosApplication(application: application)
        return true
    }
}

@main
struct iOSApp: App {

    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ComposeContentView(application: appDelegate.storymileApplication)
        }
    }
}
