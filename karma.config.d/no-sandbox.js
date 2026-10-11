// Chrome will not start as root without --no-sandbox, and the self-hosted
// Linux runner executes as root — the GitHub-hosted image ran as a normal user,
// so this never came up there. Without it the wasm tests fail with
// "Running as root without --no-sandbox is not supported".
//
// --disable-dev-shm-usage for the same environment: a container's default
// /dev/shm is 64 MB and Chrome crashes part-way through a run rather than
// failing to launch, which is the harder version of this to read.
//
// The stock ChromeHeadless launcher pins --remote-debugging-port=9222. Several
// runners share one machine, so a second job finds the port taken and Chrome
// never connects to Karma ("has not captured in 60000 ms"). Port 0 lets each
// Chrome pick a free one. captureTimeout is raised because a cold Chrome on a
// busy shared runner needs longer than 60 s to start.
config.set({
    customLaunchers: {
        ChromeHeadlessNoSandbox: {
            base: 'ChromeHeadless',
            flags: [
                '--no-sandbox',
                '--disable-dev-shm-usage',
                '--disable-gpu',
                '--remote-debugging-port=0',
            ],
        },
    },
    browsers: ['ChromeHeadlessNoSandbox'],
    captureTimeout: 210000,
    browserNoActivityTimeout: 300000,
    browserDisconnectTimeout: 60000,
    browserDisconnectTolerance: 2,
});
