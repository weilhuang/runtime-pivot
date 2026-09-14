<div align="center">

# Runtime-Pivot

<img src="plugin/src/main/resources/META-INF/pluginIcon.svg" alt="Runtime Pivot plugin logo" width="128" height="128">



[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.runtime.pivot.plugin.svg)](https://plugins.jetbrains.com/plugin/24781-runtime-pivot)
![Downloads](https://img.shields.io/github/release/wl2027/runtime-pivot.svg)
![Downloads](https://img.shields.io/badge/Java-8-brightgreen.svg?style=flat)
![Downloads](https://img.shields.io/badge/Java-21-brightgreen.svg?style=flat)
![Downloads](https://img.shields.io/badge/IDEA-2025.3-blue.svg?style=flat)

![Downloads](https://img.shields.io/badge/license-GPLv3-blue.svg)
![Downloads](https://img.shields.io/github/stars/wl2027/runtime-pivot)
[![Version](https://img.shields.io/jetbrains/plugin/v/com.runtime.pivot.plugin.svg)](https://plugins.jetbrains.com/plugin/24781-runtime-pivot)
[![GitHub](https://img.shields.io/static/v1?label=&message=GitHub&logo=github&color=black&labelColor=555)](https://github.com/wl2027/runtime-pivot) 
[![Gitee](https://img.shields.io/static/v1?label=&message=Gitee&logo=gitee&color=orange&labelColor=555)](https://gitee.com/wl2027/runtime-pivot)

</div>

## Introduction
<!-- Plugin description -->
### English:
Runtime Pivot 3.0 is a development-time debugger companion for IntelliJ IDEA 2025.3+.
It uses a dual-plane architecture: JDI for the current paused frame, and a Java Agent
data plane for global JVM queries that do **not** require hitting a breakpoint.

IDEA (OpAMP Server) and the Agent (OpAMP Client) communicate with the
[Open Agent Management Protocol](https://opentelemetry.io/docs/specs/opamp/) over
loopback WebSocket (preferred) and HTTP. Connections use a random port and a 128-bit token.

### 中文:
Runtime Pivot 3.0 面向 IntelliJ IDEA 2025.3+ 的开发期调试增强。
采用双通道：JDI 负责当前暂停栈帧；Java Agent 数据面负责全局 JVM 查询，**不必命中断点**。

IDEA 作为 OpAMP Server，Agent 作为 OpAMP Client，使用
[OpAMP](https://opentelemetry.io/docs/specs/opamp/) 经本机回环 WebSocket（优先）和 HTTP 通信，
随机端口 + 128 bit token 鉴权。
<!-- Plugin description end -->

## Features (3.0)

- **Agent data plane (no breakpoint required)**
  - ClassLoader tree, loaded classes, class-loading timeline, class dump
  - Runtime Pivot transformer list only (no `sun.instrument` enumeration of third-party transformers)
- **JDI control plane (paused frame)**
  - Expression evaluation via public `XDebuggerEvaluator`
  - Async stack frames; Drop Frame isolated behind `DropFrameCapability`
- **Presentation**
  - Runtime Pivot ToolWindow (Sessions / Classes / Objects / Probes / Console)
  - Structured protobuf DTOs over OpAMP CustomMessage; Console is a renderer, not the protocol
- **Security**
  - Loopback only, random port, 128-bit token, capability + protocol-version negotiation

2.x GIF walkthroughs under `doc/operation/` describe the previous breakpoint + `System.out` flow and are not the 3.0 protocol.

## Using The Plugin

Enable **Inject Runtime Pivot Agent on launch** in
<kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Runtime Pivot</kbd>.
The setting takes effect the next time the target JVM starts.

Open **View > Tool Windows > Runtime Pivot** (or Tools > Open Runtime Pivot).
Global class queries run from the Classes tab after the Agent connects; they do not require a breakpoint.
Object expression evaluation still requires a paused stack frame.

## Development / tests

```bash
./gradlew forbiddenApiScan unitTest integrationTest ideaUiTest --no-configuration-cache
./gradlew :plugin:buildPlugin
./gradlew :plugin:verifyPlugin
```

Agent JDK matrix (CI also runs 8/11/17/21):

```bash
./gradlew :integration-tests:test -PagentJdk=8 --no-configuration-cache
```

## FAQ

1. If an application fails to start after enabling injection, turn **Inject Runtime Pivot Agent on launch** off. Paths with spaces are copied under `~/.runtime-pivot/agent`. Token values are never written to ordinary logs.

## Compatibility

- [ ] Android Studio
- [ ] AppCode
- [ ] CLion
- [ ] DataGrip
- [ ] GoLand
- [ ] HUAWEI DevEco Studio
- [x] **IntelliJ IDEA Ultimate**
- [x] IntelliJ IDEA Community
- [x] IntelliJ IDEA Educational
- [ ] MPS
- [ ] PhpStorm
- [ ] PyCharm Professional
- [ ] PyCharm Community
- [ ] PyCharm Educational
- [ ] Rider
- [ ] RubyMine
- [ ] WebStorm


## Installation

- **Using the IDE built-in plugin system:**

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "runtime-pivot"</kbd> >
  <kbd>Install</kbd>

- **Manually:**

  Download the [latest release](https://github.com/wl2027/runtime-pivot/releases/latest) and install it manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

Restart the **IDE** after installation.


## Contributing

Welcome to contribute to the project! You can fix bugs by submitting a Pull Request (PR) or discuss new features or changes by creating an [Issue](https://github.com/wl2027/data-pivot-plugin/issues/). Look forward to your valuable contributions!

欢迎参与项目贡献！如您可以通过提交Pull Request（PR）来修复bug，或者新建 [Issue](https://github.com/wl2027/data-pivot-plugin/issues/) 来讨论新特性或变更，期待您的宝贵贡献！

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
