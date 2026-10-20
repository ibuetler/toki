import SwiftUI
import AlarmKit

struct ContentView: View {
    @State private var model = AlarmPrototype()
    @Environment(\.scenePhase) private var scenePhase
    private let teal = Color(red: 0.07, green: 0.42, blue: 0.36)

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    HStack(spacing: 12) {
                        Image(systemName: "clock.fill").font(.largeTitle).foregroundStyle(teal)
                        VStack(alignment: .leading) {
                            Text("TOKI").font(.title.bold()).tracking(3)
                            Text("Make time for what matters.").font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Text("Let’s hear it.").font(.largeTitle.bold())
                    Text("Test a real iPhone alarm before connecting your deadlines.").foregroundStyle(.secondary)
                    TimelineView(.periodic(from: .now, by: 1)) { timeline in
                        VStack(alignment: .leading, spacing: 12) {
                            Label("TEST ALARM", systemImage: "bell.badge.fill").font(.caption.bold())
                            if let target = model.target {
                                Text(target, format: .dateTime.hour().minute().second()).font(.system(size: 44, weight: .bold, design: .rounded))
                                Text(target, format: .dateTime.weekday().day().month())
                                Text(TimeZone.current.identifier).font(.caption)
                                if model.alarm?.state == .alerting {
                                    Text("Alarm ringing — dismiss using the system alarm screen.").font(.headline)
                                } else if target > timeline.date {
                                    Text("In \(Int(ceil(target.timeIntervalSince(timeline.date)))) seconds").font(.headline)
                                } else {
                                    Text("Target time reached. Check the system alarm screen.").font(.headline)
                                }
                            } else {
                                Text("Ready when you are.").font(.title2.bold())
                                Text("The test rings 30 seconds after you allow alarm access.")
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading).padding(24)
                        .foregroundStyle(.white)
                        .background(LinearGradient(colors: [Color(red: 0.07, green: 0.18, blue: 0.16), teal], startPoint: .topLeading, endPoint: .bottomTrailing), in: RoundedRectangle(cornerRadius: 24))
                    }
                    VStack(alignment: .leading, spacing: 16) {
                        LabeledContent("Alarm permission", value: model.authorizationLabel)
                        if model.authorization == .denied {
                            Button("Open TOKI Settings") {
                                if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                            }
                        }
                        Button {
                            Task { await model.scheduleTest() }
                        } label: {
                            Label(model.busy ? "Preparing alarm…" : "Test alarm in 30 seconds", systemImage: "bell.fill")
                                .frame(maxWidth: .infinity).padding(.vertical, 8)
                        }
                        .buttonStyle(.borderedProminent).tint(teal)
                        .disabled(model.busy || model.alarm != nil)
                        if model.alarm != nil {
                            Button("Cancel / dismiss test alarm", role: .destructive) { model.cancelTest() }
                                .disabled(model.busy)
                        }
                        if let message = model.message { Text(message).font(.callout).accessibilityIdentifier("alarm-message") }
                    }
                    .padding(20).background(.regularMaterial, in: RoundedRectangle(cornerRadius: 20))
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Try it on your iPhone").font(.headline)
                        Text("Tap the test button, allow alarms, then lock the screen. Confirm that you hear the alarm and can dismiss it. The system controls alarm presentation and sound.")
                        Text("This prototype does not sign in to PocketBase or create events. Use a physical iPhone to verify sound and lock-screen behavior.").font(.caption).foregroundStyle(.secondary)
                    }
                    .font(.callout)
                }
                .padding(24)
            }
            .background(Color(uiColor: .systemGroupedBackground))
            .toolbar { Button("Refresh", systemImage: "arrow.clockwise") { model.refresh() } }
        }
        .task { await model.observeAlarms() }
        .task { await model.observeAuthorization() }
        .onChange(of: scenePhase) { _, phase in if phase == .active { model.refresh() } }
    }
}

#Preview { ContentView() }
