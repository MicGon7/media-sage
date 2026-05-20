import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        let info = Bundle.main.infoDictionary
        let version = info?["CFBundleShortVersionString"] as? String ?? ""
        let build = info?["CFBundleVersion"] as? String ?? ""
        let appVersion = "v\(version) (build \(build))"
        #if DEBUG
        return MainViewControllerKt.MainViewController(isDebugBuild: true, appVersion: appVersion)
        #else
        return MainViewControllerKt.MainViewController(isDebugBuild: false, appVersion: appVersion)
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



