import CoreGraphics
import Foundation
import ImageIO
import UniformTypeIdentifiers

guard CommandLine.arguments.count == 3 else {
    fatalError("Usage: generate-macos-icon.swift input.png output.png")
}

let input = URL(fileURLWithPath: CommandLine.arguments[1])
let output = URL(fileURLWithPath: CommandLine.arguments[2])
guard
    let source = CGImageSourceCreateWithURL(input as CFURL, nil),
    let image = CGImageSourceCreateImageAtIndex(source, 0, nil),
    let context = CGContext(
        data: nil,
        width: 1024,
        height: 1024,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: CGColorSpace(name: CGColorSpace.sRGB)!,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
    )
else {
    fatalError("Cannot load the source icon or create the macOS icon canvas")
}

// Match the footprint and subtle bottom shadow of neighboring Dock icons.
let artwork = CGRect(x: 88, y: 88, width: 848, height: 848)
let outline = CGPath(roundedRect: artwork, cornerWidth: 190, cornerHeight: 190, transform: nil)
context.saveGState()
context.setShadow(offset: CGSize(width: 0, height: -12), blur: 16, color: CGColor(gray: 0, alpha: 0.3))
context.setFillColor(CGColor(gray: 0, alpha: 1))
context.addPath(outline)
context.fillPath()
context.restoreGState()

context.addPath(outline)
context.clip()
context.interpolationQuality = .high
context.draw(image, in: artwork)

guard
    let result = context.makeImage(),
    let destination = CGImageDestinationCreateWithURL(output as CFURL, UTType.png.identifier as CFString, 1, nil)
else {
    fatalError("Cannot create the macOS icon output")
}
CGImageDestinationAddImage(destination, result, nil)
guard CGImageDestinationFinalize(destination) else {
    fatalError("Cannot write the macOS icon")
}
