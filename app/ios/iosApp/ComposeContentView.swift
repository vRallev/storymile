import UIKit
import SwiftUI
import StorymileApp

struct ComposeView: UIViewControllerRepresentable {
    private var application: IosApplication

    init(application: IosApplication) {
        self.application = application
    }

    func makeUIViewController(context: Context) -> UIViewController {
        application.mainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ComposeContentView: View {
    var application: IosApplication

    init(application: IosApplication) {
        self.application = application
    }

    var body: some View {
        ComposeView(application: application)
            .ignoresSafeArea(.all)
    }
}
