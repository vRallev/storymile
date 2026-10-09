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

        for tag in ["tab-content", "tabs", "playback"] {
            let container = app.descendants(matching: .any)[tag]
            XCTAssertTrue(container.waitForExistence(timeout: 15), "Missing \(tag) container")
            XCTAssertFalse(container.frame.isEmpty)
        }

        let selectedTab = app.staticTexts["selected-tab"]
        XCTAssertTrue(selectedTab.waitForExistence(timeout: 15))
        XCTAssertEqual(selectedTab.label, "Home tab selected")

        for tag in ["tab-home", "tab-library", "tab-downloads"] {
            let tab = app.buttons[tag]
            XCTAssertTrue(tab.waitForExistence(timeout: 15), "Missing \(tag) button")
            XCTAssertTrue(tab.isHittable)
        }
        XCTAssertTrue(app.buttons["tab-home"].isSelected)
        XCTAssertEqual(app.state, .runningForeground)
    }
}
