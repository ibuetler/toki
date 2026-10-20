import AlarmKit
import Foundation
import Observation
import SwiftUI

struct TokiAlarmMetadata: AlarmMetadata {}

/// One stable identifier keeps the prototype from leaving multiple test alarms behind.
@MainActor @Observable
final class AlarmPrototype {
    private let manager = AlarmManager.shared
    private let testID = UUID(uuidString: "E827BF75-C58F-4AB0-A933-F7146A4D6210")!
    var authorization = AlarmManager.shared.authorizationState
    var alarm: Alarm?
    var busy = false
    var message: String?

    var target: Date? {
        if case .fixed(let date) = alarm?.schedule { return date }
        return nil
    }
    var authorizationLabel: String {
        switch authorization {
        case .authorized: "Allowed"
        case .denied: "Disabled in Settings"
        case .notDetermined: "Permission needed"
        @unknown default: "Unknown"
        }
    }
    func refresh() {
        authorization = manager.authorizationState
        do { alarm = try manager.alarms.first { $0.id == testID } }
        catch { message = "Could not read alarm status: \(error.localizedDescription)" }
    }
    func observeAlarms() async {
        refresh()
        for await _ in manager.alarmUpdates {
            guard !Task.isCancelled else { return }
            refresh()
        }
    }
    func observeAuthorization() async {
        for await state in manager.authorizationUpdates {
            guard !Task.isCancelled else { return }
            authorization = state
            refresh()
        }
    }
    func scheduleTest() async {
        guard !busy, alarm == nil else { return }
        busy = true
        message = nil
        defer { busy = false }
        do {
            authorization = try await manager.requestAuthorization()
            guard authorization == .authorized else {
                message = "Allow alarms for TOKI in Settings, then return and try again."
                return
            }
            // Start the 30 seconds after permission has been answered.
            let date = Date().addingTimeInterval(30)
            let alert: AlarmPresentation.Alert
            if #available(iOS 26.1, *) {
                alert = .init(title: "TOKI test alarm")
            } else {
                alert = .init(title: "TOKI test alarm", stopButton: .init(text: "Dismiss", textColor: .white, systemImageName: "stop.circle"))
            }
            let attributes = AlarmAttributes<TokiAlarmMetadata>(presentation: .init(alert: alert), metadata: TokiAlarmMetadata(), tintColor: .mint)
            let configuration = AlarmManager.AlarmConfiguration<TokiAlarmMetadata>.alarm(schedule: .fixed(date), attributes: attributes)
            alarm = try await manager.schedule(id: testID, configuration: configuration)
            message = "Alarm scheduled. Lock your iPhone to test delivery."
        } catch {
            refresh()
            message = "Could not schedule the alarm: \(error.localizedDescription)"
        }
    }
    func cancelTest() {
        guard !busy, let alarm else { return }
        do {
            if alarm.state == .alerting { try manager.stop(id: testID) }
            else { try manager.cancel(id: testID) }
            message = "Test alarm dismissed or cancelled."
            refresh()
        } catch { message = "Could not cancel the alarm: \(error.localizedDescription)" }
    }
}
