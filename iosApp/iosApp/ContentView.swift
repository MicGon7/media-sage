import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        #if DEBUG
        MainViewControllerKt.MainViewController(isDebugBuild: true)
        #else
        MainViewControllerKt.MainViewController(isDebugBuild: false)
        #endif
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()
    }
}



