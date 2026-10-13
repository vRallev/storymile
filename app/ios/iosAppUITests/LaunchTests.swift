import XCTest

final class LaunchTests: XCTestCase {
    func testLaunchShowsHomeAndNavigation() {
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

        for tag in ["tab-content", "home-search", "tabs"] {
            let container = app.descendants(matching: .any)[tag]
            XCTAssertTrue(container.waitForExistence(timeout: 15), "Missing \(tag) container")
            XCTAssertFalse(container.frame.isEmpty)
        }

        // Compose exposes heading semantics as an Other accessibility element on iOS.
        let selectedTab = app.descendants(matching: .any)["tab-title"]
        XCTAssertTrue(selectedTab.waitForExistence(timeout: 15))
        XCTAssertEqual(selectedTab.label, "Welcome to Storymile")

        for tag in ["tab-home", "tab-library", "tab-downloads"] {
            let tab = app.buttons[tag]
            XCTAssertTrue(tab.waitForExistence(timeout: 15), "Missing \(tag) button")
            XCTAssertTrue(tab.isHittable)
        }
        XCTAssertTrue(app.buttons["tab-home"].isSelected)
        XCTAssertFalse(app.descendants(matching: .any)["playback"].exists)
        XCTAssertEqual(app.state, .runningForeground)
    }
}
