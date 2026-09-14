<div align="center">

# Runtime-Pivot

<img src="docs/assets/logo-readme.svg" alt="Runtime Pivot plugin logo" width="128" height="128">



[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.runtime.pivot.plugin.svg)](https://plugins.jetbrains.com/plugin/24781-runtime-pivot)
![Downloads](https://img.shields.io/github/release/wl2027/runtime-pivot.svg)
![Downloads](https://img.shields.io/badge/Java-8-brightgreen.svg?style=flat)
![Downloads](https://img.shields.io/badge/Java-17-brightgreen.svg?style=flat)

![Downloads](https://img.shields.io/badge/license-GPLv3-blue.svg)
![Downloads](https://img.shields.io/github/stars/wl2027/runtime-pivot)
[![Version](https://img.shields.io/jetbrains/plugin/v/com.runtime.pivot.plugin.svg)](https://plugins.jetbrains.com/plugin/24781-runtime-pivot)
[![GitHub](https://img.shields.io/static/v1?label=&message=GitHub&logo=github&color=black&labelColor=555)](https://github.com/wl2027/runtime-pivot) 
[![Gitee](https://img.shields.io/static/v1?label=&message=Gitee&logo=gitee&color=orange&labelColor=555)](https://gitee.com/wl2027/runtime-pivot)

</div>

> **Note / 说明:** 3.0 foundation is on `main`. Current IDEA/Java baseline, settings, ToolWindow, and OpAMP notes are in [Runtime Pivot 3.0 notes](#runtime-pivot-30-notes) at the end. The GIF tutorials below are the 2.x walkthroughs and are kept on purpose. / 3.0 基础已合入 `main`。当前基线、设置项、ToolWindow 与 OpAMP 说明见文末 [Runtime Pivot 3.0 notes](#runtime-pivot-30-notes)。下方 GIF 教程是 2.x 操作文档，刻意保留。

## Introduction
<!-- Plugin description -->
### English:
runtime-pivot is a runtime enhancement toolkit that provides convenient features for developers when debugging code.

The current features are divided into four dimensions:
- **program**: Analyzes instrument data during program runtime.
- **class**: Analyzes bytecode information of classes in memory during program runtime.
- **session**: Analyzes and manipulates code invocation information during debugging sessions in program runtime.
- **object**: Analyzes and manipulates object memory during program runtime.

Comparison with similar tools:

|       | runtime-pivot  | Arthas          | JProfiler          |
|:------|:---------------|:----------------|:-------------------|
| Usage | Debugging tool for development phase | Online issue diagnosis tool | Performance analysis and reporting tool |
| Features | Analysis and memory operations at specific breakpoints | Diagnosis and troubleshooting for JVM issues | Performance tuning and reporting for JVM |

Detailed operation documents: [https://github.com/wl2027/runtime-pivot](https://github.com/wl2027/runtime-pivot)

### 中文:
runtime-pivot 是一个运行时增强工具集,为开发人员在调试代码时提供便捷的功能.

当前功能分为四个维度:
- program 分析程序运行时instrument数据
- class 分析程序运行时内存类字节码信息
- session 分析和操作程序运行中调试会话的代码调用信息
- object 分析和操作程序运行时对象内存信息

类似工具差异说明:

|     | runtime-pivot  | Arthas        | JProfiler      |
|:----|:--------------|:--------------|:---------------|
| 定位  | 开发阶段的调试工具 | 线上问题诊断工具      | 性能分析和报告工具      |
| 特点  | 针对特定断点的分析和内存操作 | 针对JVM的问题诊断和定位 | 针对JVM性能调优和报表分析 |
| ... ||               |

详细操作文档: [https://github.com/wl2027/runtime-pivot](https://github.com/wl2027/runtime-pivot)

<!-- Plugin description end -->

## Features
- **program**
  - [x] View the runtime classLoader tree structure information. 查看运行时的classLoader树结构信息.
  - [x] View the runtime classLoader loaded classes tree structure information. 查看运行时的classLoader加载类的树结构信息.
  - [x] View the runtime transformers list information. 查看运行时的transformers列表信息.
- **class**
  - [x] View the runtime class loading chain information. 查看运行时class加载链路信息.
  - [x] Dump the runtime class bytecode information. 转储运行时class字节码信息.
- **session**
  - [x] Monitor the runtime code invocations. 监控运行时代码调用.
  - [x] Operate the runtime breakpoints list. 操作运行时断点列表.
- **object**
  - [x] View the runtime object memory layout. 查看运行时对象内存布局.
  - [x] Dump the runtime object JSON data. 转储运行时对象json数据.
  - [x] Load JSON data to update the runtime object. 加载json数据更新运行时对象.

## 3.0 实施路线图

当前 `main` 已合入 3.0 **OpAMP foundation**（多模块、loopback 通信、ToolWindow 骨架）。尚未完成的能力、与规格 Phase 0–8 的对照、以及后续 PR 应遵循的 Wave 顺序，见 [`doc/runtime-pivot-3.0-roadmap.md`](doc/runtime-pivot-3.0-roadmap.md)。架构与门禁仍以 [`doc/runtime-pivot-3.0-refactoring-plan.md`](doc/runtime-pivot-3.0-refactoring-plan.md) 为准。

## Using The Plugin

open attach agent.

(Whether to enable the program, class, and object functions of runtime-pivot for the project program)

![0.attach_agent.gif](doc%2Foperation%2F0.attach_agent.gif)

Using the open-source project [xxl-job](https://github.com/xuxueli/xxl-job) as an example, run the program and enter the breakpoint. 以开源项目[xxl-job](https://github.com/xuxueli/xxl-job)为例,运行程序并进入断点。

1.1 View the runtime classLoader tree structure information, the operation result is printed to the console. 查看运行时的 classLoader 树结构信息，操作结果打印到控制台。
![1.1 CLT.gif](doc%2Foperation%2F1.1%20CLT.gif)

1.2 View the runtime classLoader loaded classes tree structure information, the operation result is printed to the console. 查看运行时 classLoader 加载类的树结构信息，操作结果打印到控制台。
![1.2 CLTCT.gif](doc%2Foperation%2F1.2%20CLTCT.gif)

1.3 View the runtime transformers list information, the operation result is printed to the console. 查看运行时 transformers 列表信息，操作结果打印到控制台。
![1.3 TRS.gif](doc%2Foperation%2F1.3%20TRS.gif)

2.1 View the runtime class loading chain information, applicable to class files, search boxes, and runtime objects. The operation result is printed to the console. 查看运行时 class 加载链路信息，可作用于类文件、搜索框、运行时对象，操作结果打印到控制台。
![2.1 CPS.gif](doc%2Foperation%2F2.1%20CPS.gif)

2.2 Dump the runtime class bytecode information, applicable to class files, search boxes, and runtime objects. The dump path is the ```.runtime``` directory of the current project and is printed to the console. 转储运行时 class 字节码信息，可作用于类文件、搜索框、运行时对象。转储路径为当前项目的 ```.runtime``` 目录，并打印到控制台。
![2.2 CFD.gif](doc%2Foperation%2F2.2%20CFD.gif)

3.1 Monitor runtime code invocations, outputting overall time and time distribution between breakpoints. 监控运行时代码调用，输出总体时间和断点间时间分布。
![3.1 MT.gif](doc%2Foperation%2F3.1%20MT.gif)

3.2 Operate the breakpoint list at runtime, output the breakpoint list information of the currently selected stack frame, click to navigate to the code location, and double-click pop to select the breakpoint stack frame. 操作运行时断点列表，输出当前选择栈帧的断点列表信息，单击可导航至代码位置,双击pop选择断点栈帧.
![3.2 SL.gif](doc%2Foperation%2F3.2%20SL.gif)

4.1 View the runtime object memory layout, including object size, occupied size, and object header information. 查看运行时对象内存布局，包括对象大小、占用大小、对象头信息。
![4.1 OI.gif](doc%2Foperation%2F4.1%20OI.gif)

4.2 Dump the runtime object's JSON data. The dump path is the ```.runtime``` directory of the current project and is printed to the console. 转储运行时对象的 JSON 数据，转储路径为当前项目的 ```.runtime``` 目录，并打印到控制台。
![4.2 OS.gif](doc%2Foperation%2F4.2%20OS.gif)

4.3 Load JSON data to update the runtime object. The default path is the ```.runtime``` directory of the current project. When loading collection data, empty collections will lose their generics. 加载 JSON 数据更新运行时对象，默认路径为当前项目的 ```.runtime``` 目录，加载集合数据时空集合会擦除泛型。
![4.3 OL.gif](doc%2Foperation%2F4.3%20OL.gif)


## FAQ

1. If the IDEA program fails to start after installing the plugin, please set ```Attach Agent``` in <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Runtime-Pivot Configuration</kbd> to false. 如果安装插件后IDEA程序启动失败,请将 <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Runtime-Pivot Configuration</kbd> 中的  ```Attach Agent``` 设置为false
![img.png](doc/faq/1 error start.png)
   The occurrence of this situation may be caused by spaces, Chinese characters or illegal characters in the agent path. Issues can be submitted for problem investigation.  这种情况的出现可能是agent路径有空格或者中文或者非法字符导致的,可以提交issue以进行问题排查
![0.attach_agent.gif](doc%2Foperation%2F0.attach_agent.gif)

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

## Runtime Pivot 3.0 notes

### 3.0 补充说明

This section covers **conflicts and new facts** on current `main` relative to the 2.x tutorials above. It does not replace those walkthroughs. / 本节只补充当前 `main` 与上方 2.x 教程冲突或新增的事实，不替代 GIF 教程。

**Baseline / 基线**

- IntelliJ IDEA **2025.3+** (`pluginSinceBuild = 253`). The 2.x Java 17 badge above is historical; the **plugin** now compiles with **Java 21**. Agent/protocol bytecode `release` remains **8**, and CI still runs an Agent JDK matrix (8/11/17/21).
- 插件面向 IDEA 2025.3+ / build 253；插件 Java 21。Agent/protocol 仍按 Java 8 发布字节码。

**Settings rename / 设置项更名**

- Enable **Inject Runtime Pivot Agent on launch** in <kbd>Settings/Preferences</kbd> > <kbd>Tools</kbd> > <kbd>Runtime Pivot</kbd>. It takes effect the next time the target JVM starts. The 2.x **Attach Agent** checkbox is migrated onto this option.
- 2.x FAQ 里的 `Attach Agent` / `Runtime-Pivot Configuration` 已更名为 **Inject Runtime Pivot Agent on launch**（中文：**启动时注入 Runtime Pivot Agent**），设置页为 **Runtime Pivot**。若启动失败，关闭该选项。带空格的 Agent 路径会复制到 `~/.runtime-pivot/agent`。Token 不会写入普通日志。

**Primary UI / 主界面**

- The 3.0 primary UI is the **Runtime Pivot ToolWindow**: **Sessions / Classes / Objects / Probes / Console**. Open it from **View > Tool Windows > Runtime Pivot** or **Tools > Open Runtime Pivot**.
- The 2.x menu + GIF flows (attach-agent checkbox in the old configuration UI, editor-popup actions, results printed to the target process console) are **historical tutorials**. They may not match 3.0 entry points yet.

**OpAMP communication / 通信**

- IDEA is the OpAMP Server; the Agent is the OpAMP Client. They speak [Open Agent Management Protocol (OpAMP)](https://opentelemetry.io/docs/specs/opamp/) over **loopback** WebSocket (preferred) and HTTP, with a **random port** and a **128-bit token**.
- 仅本机回环绑定；非 loopback 对端会被拒绝。

**Honest status / 现状（未完成项不当作已完成）**

The 3.0 **foundation** has landed (protocol, Agent data plane, ToolWindow shell, settings migration). **Not all 2.x GIF features are fully re-wired in the 3.0 UI yet.** What is actually on `main` today:

- **Classes** tab: ClassLoader tree and loaded-class list over OpAMP. These global queries do **not** require a breakpoint. Transformer list, class-loading timeline, and class dump exist as Agent commands; they are **not** yet exposed as ToolWindow actions matching the 2.x GIFs.
- **Objects** tab: expression evaluation via the public JDI/`XDebuggerEvaluator` API on a **paused** stack frame. Object memory layout / JSON store / JSON load from the 2.x GIFs are **not** re-wired in this UI yet.
- **Sessions** tab: OpAMP loopback server status, capabilities, and health — not the 2.x stack-list / monitoring menus.
- **Probes** tab: placeholder (Agent instrumentation / timers are planned; not shipped as the 2.x monitoring GIF).
- 2.x GIF features such as monitoring between breakpoints, stack-list pop, object layout, object JSON dump/load, and the old transformer-list console dump should be treated as **2.x tutorials**, not as a claim that the 3.0 ToolWindow already provides the same entry points.

See [doc/runtime-pivot-3.0-refactoring-plan.md](doc/runtime-pivot-3.0-refactoring-plan.md) for the remaining work. (`doc/runtime-pivot-3.0-roadmap.md` is not present on this branch.)

**Development / tests**

```bash
./gradlew forbiddenApiScan unitTest integrationTest ideaUiTest --no-configuration-cache
./gradlew :plugin:buildPlugin
./gradlew :plugin:verifyPlugin
```

Agent JDK matrix (CI also runs 8/11/17/21):

```bash
./gradlew :integration-tests:test -PagentJdk=8 --no-configuration-cache
```

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
