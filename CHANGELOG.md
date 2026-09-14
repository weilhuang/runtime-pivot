<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# runtime-pivot-plugin Changelog

## [Unreleased]
### Added
- Runtime Pivot 3.0 OpAMP foundation (see 3.0.0 notes)

## [3.0.0]
### Added
- OpAMP-based IDEA↔Agent protocol (WebSocket + HTTP) with loopback bind, random port, and 128-bit token auth
- Multi-module Gradle layout: `protocol`, `agent-*`, `plugin-*`, `integration-tests`, `test-apps`
- Runtime Pivot ToolWindow (Sessions / Classes / Objects / Probes / Console)
- Agent source built from this repository (shaded jar) instead of an opaque fat jar
- Unit, integration/premain, and headless UI tests; Agent JDK matrix in CI
- Forbidden-API scan and JetBrains-template-style Build / Verify / Release workflows

### Changed
- Platform baseline is IntelliJ IDEA 2025.3 / build 253+, plugin Java 21, Agent/protocol Java 8
- Settings checkbox renamed to **Inject Runtime Pivot Agent on launch**
- Global JVM queries no longer require hitting a breakpoint

### Removed
- Custom newline-framed handshake/command protocol from the 3.0 plan draft
- Production use of `com.intellij.*.impl`, copied `@TestOnly` debugger utilities, and debugger-tree internals
- Bundled untraceable `libs/runtime-pivot-agent-*-all.jar`

[Unreleased]: https://github.com/weilhuang/runtime-pivot/compare/3.0.0...HEAD
[3.0.0]: https://github.com/weilhuang/runtime-pivot/compare/2.1.0...3.0.0
[2.0.0]: https://github.com/wl2027/runtime-pivot/compare/1.1.2...2.0.0
[1.1.2]: https://github.com/wl2027/runtime-pivot/compare/1.1.1...1.1.2
[1.1.1]: https://github.com/wl2027/runtime-pivot/compare/1.1.0...1.1.1
[1.1.0]: https://github.com/wl2027/runtime-pivot/compare/1.0.1...1.1.0
[1.0.1]: https://github.com/wl2027/runtime-pivot/compare/1.0.0...1.0.1
[1.0.0]: https://github.com/wl2027/runtime-pivot/commits/1.0.0
