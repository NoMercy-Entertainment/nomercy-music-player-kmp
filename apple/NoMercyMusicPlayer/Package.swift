// swift-tools-version:5.9
import Foundation
import PackageDescription

// Two Apple tiers, the same shape the video package has.
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
let releasing = ProcessInfo.processInfo.environment["NOMERCY_SPM_RELEASE"] == "1"

let engine: Target = releasing
    ? .binaryTarget(
        name: "NoMercyMusicPlayer",
        url: "https://github.com/NoMercy-Entertainment/nomercy-music-player-kmp/releases/download/v2.0.0-rc.1/NoMercyMusicPlayer.xcframework.zip",
        checksum: "REPLACED_BY_RELEASE_JOB"
    )
    : .binaryTarget(
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
            path: "Tests/NoMercyMusicPlayerTests"
        ),
    ]
)
