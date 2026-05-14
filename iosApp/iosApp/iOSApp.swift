import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        let info = Bundle.main.infoDictionary
        let supabaseUrl = info?["SUPABASE_URL"] as? String ?? ""
        let supabaseAnonKey = info?["SUPABASE_ANON_KEY"] as? String ?? ""
        MainViewControllerKt.doInitKoin(supabaseUrl: supabaseUrl, supabaseAnonKey: supabaseAnonKey)
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
