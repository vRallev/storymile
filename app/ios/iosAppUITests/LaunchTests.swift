import XCTest

final class LaunchTests: XCTestCase {
    func testLaunchShowsEmptyAppContainers() {
        continueAfterFailure = false
        let app = XCUIApplication()
        addTeardownBlock { [self] in
            let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            screenshot.name = "Initial screen"
            screenshot.lifetime = .keepAlways
            add(screenshot)

            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.name = "Accessibility hierarchy"
            hierarchy.lifetime = .keepAlways
            add(hierarchy)
        }

        XCUIDevice.shared.orientation = .portrait
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        XCTAssertTrue(app.wait(for: .runningForeground, timeout: 15))

        for tag in ["library", "tabs", "playback"] {
            let container = app.descendants(matching: .any)[tag]
            XCTAssertTrue(container.waitForExistence(timeout: 15), "Missing \(tag) container")
            XCTAssertFalse(container.frame.isEmpty)
            XCTAssertEqual(container.staticTexts.count, 0)
        }
        XCTAssertEqual(app.state, .runningForeground)
    }
}
