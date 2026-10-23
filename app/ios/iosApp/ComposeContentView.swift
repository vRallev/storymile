import UIKit
import SwiftUI
import StorymileApp

struct ComposeView: UIViewControllerRepresentable {
    private var startup: ApplicationStartup

    init(startup: ApplicationStartup) {
        self.startup = startup
    }

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController(startup: startup)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ComposeContentView: View {
    var startup: ApplicationStartup

    init(startup: ApplicationStartup) {
        self.startup = startup
    }

    var body: some View {
        ComposeView(startup: startup)
            .ignoresSafeArea(.all)
    }
}
