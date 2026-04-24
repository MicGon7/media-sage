import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        let serverBaseUrl = ProcessInfo.processInfo.environment["SERVER_BASE_URL"] ?? "http://localhost:8080"
        MainViewControllerKt.doInitKoin(serverBaseUrl: serverBaseUrl)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}