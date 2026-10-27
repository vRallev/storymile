import UIKit
import SwiftUI
import StorymileApp

struct ComposeView: UIViewControllerRepresentable {
    private var kmpApplication: KmpApplication

    init(kmpApplication: KmpApplication) {
        self.kmpApplication = kmpApplication
    }

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.mainViewController(kmpApplication: kmpApplication)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ComposeContentView: View {
    var kmpApplication: KmpApplication

    init(kmpApplication: KmpApplication) {
        self.kmpApplication = kmpApplication
    }

    var body: some View {
        ComposeView(kmpApplication: kmpApplication)
            .ignoresSafeArea(.all)
    }
}
