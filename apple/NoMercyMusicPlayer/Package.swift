// swift-tools-version:5.9
import PackageDescription

// Two Apple tiers, the same shape the video package has. This manifest resolves
// against a locally assembled XCFramework until the release workflow publishes
// an Apple binary and stamps a real checksum.
//
// NoMercyMusicPlayer is the headless engine; NoMercyMusicPlayerUI is the drop-in
// SwiftUI player over it. The UI product carries a different name from the
// video repo's on purpose — an application taking both would otherwise have two
// products called NoMercyPlayer and no way to say which it meant.
//
// The engine framework statically links core, so an application importing this
// gets the core symbols with it and there is deliberately no second binaryTarget
// for core: two copies of the same symbols in one process is a linker error
// nobody can read.
let engine: Target = .binaryTarget(
    name: "NoMercyMusicPlayer",
    path: "../../build/XCFrameworks/release/NoMercyMusicPlayer.xcframework"
)

let package = Package(
    name: "NoMercyMusicPlayer",
    platforms: [.iOS(.v15), .tvOS(.v15)],
    products: [
        .library(name: "NoMercyMusicPlayer", targets: ["NoMercyMusicPlayer"]),
        .library(name: "NoMercyMusicPlayerUI", targets: ["NoMercyMusicPlayerUI"]),
    ],
    targets: [
        engine,
        .target(name: "NoMercyMusicPlayerUI", dependencies: ["NoMercyMusicPlayer"], path: "Sources/NoMercyMusicPlayer"),
        .testTarget(
            name: "NoMercyMusicPlayerTests",
            dependencies: ["NoMercyMusicPlayerUI"],
            path: "Tests/NoMercyMusicPlayerTests",
            // The contract travels with the bundle. A Swift test has no working
            // directory it can rely on — xcodebuild runs it from wherever it
            // likes — and the Kotlin gates read the same bytes from the
            // repository root.
            resources: [.copy("conformance/Resources/contract.json")]
        ),
    ]
)
