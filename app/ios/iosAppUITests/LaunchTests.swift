import XCTest

final class LaunchTests: XCTestCase {
    func testLaunchShowsEmptyLibrary() {
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

        let emptyLibrary = app.descendants(matching: .any)["libraryEmpty"]
        XCTAssertTrue(emptyLibrary.waitForExistence(timeout: 15))
        XCTAssertEqual(emptyLibrary.label, "Your library is empty.")
        XCTAssertTrue(emptyLibrary.isHittable)

        let title = app.staticTexts["Library"]
        XCTAssertTrue(title.waitForExistence(timeout: 15))
        XCTAssertTrue(title.isHittable)
        XCTAssertEqual(app.state, .runningForeground)
    }
}
