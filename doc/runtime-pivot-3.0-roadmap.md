# Runtime Pivot 3.0 落地路线图（Foundation 之后）

> 文档状态：**实施路线图**（非完成宣告）  
> 基准提交：`f43a23cd416bc153a9df3e2e1c72daf396630f43`（`main`，已合并 PR #6 *Runtime Pivot 3.0: OpAMP protocol foundation*）  
> 基准版本：`pluginVersion=3.0.0`，`platformVersion=2025.3`，`pluginSinceBuild=253`（见 `gradle.properties`）  
> 核实日期：2026-09-14  
> 适用对象：后续设计、实现、优化、重构的开发者或 AI 编码代理

## 0. 与 `runtime-pivot-3.0-refactoring-plan.md` 的关系

| 文档 | 角色 |
| --- | --- |
| [`runtime-pivot-3.0-refactoring-plan.md`](./runtime-pivot-3.0-refactoring-plan.md) | **3.0 架构 / 公开 API / 安全 / 测试 / 验收门禁规格**。OpAMP 版。后续实现不得绕过其中的硬约束。 |
| **本文** | **Foundation 合入后的落地优先级、缺口清单与分波计划**。以当前 `main` 源码核实为准，不以 changelog 或记忆为准。 |

冲突处理：

1. 公开 API、loopback+token、禁止 `com.intellij.*.impl` / `sun.instrument` / debugger-tree 内部节点、不枚举第三方 Transformer 等 **门禁** 仍以规格为准。
2. **做什么、先做什么、当前真实完成度** 以本文为准。
3. 若规格 Phase 验收与仓库现状冲突，以 **§4 决策表** 为准（该表只记录已核实项）。规格原文不在本 PR 中改写，避免双源。

本文 **不是** 3.0 已完成的声明。`pluginVersion` 已是 `3.0.0`，但 §23 完成定义仍有多项未满足。对外宣称「3.0 功能完成」必须等到 §9。

---

## 1. 现状快照（已核实）

### 1.1 模块与构建

Gradle 多模块已落地（`settings.gradle.kts`）：

```text
runtime-pivot
├── protocol
├── agent/agent-bootstrap
├── agent/agent-core
├── agent/agent-probe
├── plugin                ← IntelliJ 宿主 + plugin.xml + 测试
├── plugin/plugin-core
├── plugin/plugin-debugger
├── plugin/plugin-ui
├── integration-tests
└── test-apps
```

| 项 | 核实值 | 证据 |
| --- | --- | --- |
| 插件版本 | `3.0.0` | `gradle.properties` |
| IDEA 基线 | `2025.3` / `since-build=253`，`untilBuild=null` | `gradle.properties`，`plugin/build.gradle.kts` |
| 平台工件 | `intellijIdea()`（2025.3 起不再单独发 IC） | `gradle.properties` 注释；各 plugin 模块 |
| Gradle / Kotlin / Platform Plugin | Gradle 9.0.0，Kotlin 2.1.20，IntelliJ Platform Gradle Plugin 2.18.1 | `gradle-wrapper.properties`，`gradle/libs.versions.toml` |
| 插件字节码 | Java 21 | `plugin/build.gradle.kts` `options.release.set(21)` |
| protocol / agent 字节码 | Java 8 | 各 agent/protocol `build.gradle.kts` `release.set(8)` |
| Agent 产物 | `:agent:agent-bootstrap:shadowJar` → 插件资源 `/agent/runtime-pivot-agent.jar` | `plugin/build.gradle.kts` `copyAgentJar` |
| shade | protobuf / Java-WebSocket / slf4j 已 relocate | `agent/agent-bootstrap/build.gradle.kts` |
| ASM | **无依赖** | `gradle/libs.versions.toml` 无 ASM |
| Kotlin 源码 | **无** `.kt` 生产文件 | 仓库检索 |
| 旧 fat jar | 已删除 `libs/runtime-pivot-agent-*-all.jar` | 目录不存在 |
| 旧 `src/` 单体插件 | 已删除 | 根目录无 `src/` |

### 1.2 OpAMP 角色与传输

| 角色 | 实现 | 证据 |
| --- | --- | --- |
| OpAMP Server（IDEA） | `OpampServer` 绑定 loopback，随机 WS/HTTP 端口 | `protocol/.../OpampServer.java`；`ConnectionConfig` 拒绝非 `127.0.0.1`/`localhost` |
| OpAMP Client（Agent） | `OpampClient` 优先 WebSocket，失败则 HTTP POST `/v1/opamp` | `OpampClient.start()` |
| 鉴权 | `Authorization: Bearer <128-bit hex>` + `X-Runtime-Pivot-Session-Id` | `AuthTokens`；token 不进 `toLogString()` |
| 协议版本 | identifying attribute `runtime.pivot.protocol.version=3` | `ProtocolVersion` |
| 标准 bits | Agent：`ReportsStatus \| ReportsHealth \| ReportsHeartbeat`；Server：`AcceptsStatus \| OffersConnectionSettings` | `PivotCapabilities` |
| Custom capability | `io.runtime.pivot.v1` + `*.classes` / `*.events` / `*.objects` / `*.probes` | `PivotCapabilities.agentCustomCapabilities()` |
| 帧格式 | WS：varint header `0` + protobuf；HTTP：纯 protobuf | `OpampWire` |
| 最大消息 | 默认 4 MiB，超限拒绝，**无分块** | `ConnectionConfig.DEFAULT_MAX_MESSAGE_BYTES` |
| HTTP poll | 回退传输每 **2 秒** poll 一次；HTTP sink **每次响应只冲一条** outbound | `OpampClient.HTTP_POLL_SECONDS=2`；`AgentSession.flushOutgoing()` `canPush()==false` 分支 |
| 心跳 | 默认 30s，Server 可通过 `OpAMPConnectionSettings.heartbeat_interval_seconds` 协商 | `ConnectionConfig.DEFAULT_HEARTBEAT_SECONDS` |
| 注入 | `RuntimeJavaAgentConfig`（`JavaProgramPatcher`）写 `-javaagent:` | `plugin.xml` `java.programPatcher` |
| premain / agentmain | 均存在；Manifest 含 `Premain-Class` 与 `Agent-Class` | `AgentMain`；`shadowJar` manifest |
| 动态 Attach UX | **无**（无 `jdk.attach` 调用、无设置项） | 仓库检索 |

握手 / ping / 错误 token / 缺协议版本 / WS 与 HTTP disconnect 有协议测试：`protocol/src/test/java/com/runtime/pivot/protocol/OpampHandshakeTest.java`。

**未核实到的行为：** `OpampClient` 在 `onClose` 只把 `handshakeComplete=false`，**没有自动重连**。规格 Phase 3「连接断开可恢复」目前只做到会话清理，未做到客户端恢复。

### 1.3 Agent 命令（数据面）

`AgentStarter` 注册的 handler：

| 命令常量 | 值 | Handler | 状态 |
| --- | --- | --- | --- |
| `PivotCommands.PING` | `ping` | `PingHandler` | 已通 |
| `CLASS_LOADERS` | `class.loaders` | `ClassQueryService.loadersHandler()` | 已通 |
| `CLASS_LOADED` | `class.loaded` | `loadedHandler()`，支持 `LoadedClassesRequest`（`name_prefix` / `loader_id` / `limit`，默认 10_000，超出记 `truncated`） | 已通 |
| `CLASS_DUMP` | `class.dump` | `dumpHandler()`：类名 **精确匹配**；多 ClassLoader 必须带 `loader_id`；`retransform` + finally 移除临时 Transformer；返回 bytecode + SHA-256 + `modifiable` | 已通（内存字节，**不落盘**） |
| `CLASS_LOADING_TIMELINE` | `class.loading.timeline` | `timelineHandler()`：有界环缓（默认 10_000），弱引用 ClassLoader，记 `dropped_events` | 已通 |
| `TRANSFORMER_LIST` | `transformer.list` | `transformerHandler()`：只列 `TransformerRegistry` 自己登记的项；返回 retransform/redefine 能力 | 已通 |

未注册对象 / Probe 命令。未知命令走 `UnsupportedCommandException` → `ERROR_UNSUPPORTED`（`OpampClient.CommandDispatcher`）。

`runtime_pivot.proto` **没有** 规格 §10.4 中的 `ObjectLayoutResult` / `ObjectSnapshotResult` / `ObjectMutationPreview` / `ProbeStatisticsResult`。对象与 Probe 的 DTO 尚未建模。

`CommandProgress` / `CancelCommand` / `EventBatch` 已在 proto 定义：

- `CustomMessages` 有 request/accepted/result/error/cancel，**没有** progress / EventBatch 编码辅助方法。
- `OpampServer.AgentSession.handleCustom` 处理 result / error / EventBatch；**忽略** `CommandAccepted` 与 `CommandProgress`。
- Agent **从不发送** `EventBatch`。类加载事件只能通过 `class.loading.timeline` **拉取**。
- `CommandContext` 支持取消；`dispatch` 仅在 handler **入口** `throwIfCancelled()`。`ClassQueryService.dump()` 在 `getAllLoadedClasses` / `retransform` 过程中不检查取消。
- UI 没有任何进度条或取消按钮。`RuntimePivotOpampService.cancel(requestId)` 存在但无人调用。

### 1.4 宣称的 capability vs 真实能力

`PivotCapabilities.agentCustomCapabilities()` / `serverCustomCapabilities()` 固定返回：

```text
io.runtime.pivot.v1.classes
io.runtime.pivot.v1.events
io.runtime.pivot.v1.objects
io.runtime.pivot.v1.probes
io.runtime.pivot.v1
```

| capability | 真实情况 |
| --- | --- |
| `classes` | Agent 五条 class/transformer 命令已实现 |
| `events` | 有界缓冲 + timeline **拉取** 已实现；**无** `EventBatch` 推送 |
| `objects` | **无** ObjectHandle / Layout / Store / Load；无对应命令与 proto |
| `probes` | `NoOpProbeEngine` 只把 `ProbeDefinition` 放进 `ConcurrentHashMap`，无字节码插桩 |

### 1.5 JDI 控制面（plugin-debugger）

| 类 | 作用 | UI 接线 |
| --- | --- | --- |
| `DebuggerSessions` | `XDebugSession.DATA_KEY` / `XDebuggerManager.getCurrentSession()` | 未被面板调用 |
| `ExpressionEvaluation` | 公开 `XDebuggerUtil.createExpression` + `XDebuggerEvaluator` | **仅** `ObjectsPanel` Evaluate |
| `AsyncStackFrames` | `XExecutionStack.computeStackFrames`，不阻塞 EDT | **未接线** |
| `DropFrameCapability` | 隔离 Experimental `XDropFrameHandler` | **未接线**；forbidden-API 扫描白名单仅此文件 |
| `SessionLifecycleListener` | `processStopped` / `currentSessionChanged` 空方法体 | 已在 `plugin.xml` `projectListeners` 注册，无 Pause Interval、无资源释放以外的逻辑（OpAMP 由 project service `dispose()` 关闭） |

生产代码无 `com.intellij.*.impl` import，无 `sun.instrument`，无复制的 `XDebuggerTestUtil`。`forbiddenApiScan` 覆盖上述规则。

### 1.6 ToolWindow UI

`RuntimePivotToolWindowFactory` 注册底部 ToolWindow `Runtime Pivot`，标签：Sessions / Classes / Objects / Probes / Console。入口：`OpenRuntimePivotAction`（Tools 菜单 + EditorPopup）。

| 面板 | 已接 | 未接 / 占位 |
| --- | --- | --- |
| `SessionsPanel` | 状态、negotiated capabilities、health；按钮 **Start loopback server** → `ensureStarted()` | 当前调试会话列表、调用栈、Drop Frame、Pause Intervals、Suspend ALL 提示 |
| `ClassesPanel` | 按钮 **Load ClassLoaders** → `CLASS_LOADERS`；**Load classes** → `CLASS_LOADED`（payload 为 **空字节**，因此无 prefix/loader/limit） | Dump、Timeline、Transformers、过滤、分页、进度/取消、未连接时禁用 |
| `ObjectsPanel` | 表达式输入 + Evaluate（需当前暂停会话） | Layout / Store / Load Preview / 最近表达式 / 编辑器选区预填 / 危险确认 |
| `ProbesPanel` | 一句占位文案：「will be completed in Phase 6」 | 列表、启停、统计、Dashboard、导出 |
| `ConsolePanel` | `TextConsoleBuilderFactory` 的 `ConsoleView` + Clear；监听连接状态打印一行 | 暂停输出、过滤、可导航链接、命令历史 |

设置 UI `RuntimePivotConfigurable` **只** 暴露 `injectAgentOnLaunch`。`RuntimePivotSettings.State` 另有 `enableAgentCommunication`、`eventBufferSize`（默认 10_000），以及 2.x `attachAgent` → `injectAgentOnLaunch` 迁移（`loadState` / `migrateFromLegacy()`）。规格 §19 的 probe sample rate、object snapshot limits、output directory、Keep session history、Run Configuration Override **均无**。

### 1.7 测试与 CI

| 任务 / 工作流 | 覆盖 | 缺口 |
| --- | --- | --- |
| `:protocol:test` | 握手 WS/HTTP、错误 token、缺版本、disconnect、PathSafety、有界队列、capability bits、token 128-bit | 无 cancel/progress/chunking/EventBatch 发送测试；无 dump 命令测试 |
| `:agent:agent-core:test` | `ClassLoadingRecorder` 丢弃计数 | 无 `ClassQueryService` dump/精确匹配测试 |
| `:agent:agent-probe:test` | `ProbeDefinition` kind 校验 + NoOp 存取 | 无插桩 |
| `:plugin:unitTest` | 常量、legacy settings 迁移、loopback URL | — |
| `:plugin:integrationTest` | 插件加载、service 注册、Open action、OpAMP bind loopback | 无真实 ToolWindow 状态机 |
| `:plugin:ideaUiTest` | **自造** `JTabbedPane` 五个空页，**不实例化** `ClassesPanel` 等 | 规格 §21.5 的连接/暂停/取消/分页均未测 |
| `:integration-tests:test` `AgentPremainIT` | 目标 JVM premain：ping、loaders、loaded、transformer.list | **无** dump、timeline、object、probe、agentmain |
| CI `build.yml` | `buildPlugin`、`forbiddenApiScan unitTest integrationTest ideaUiTest`、Agent JDK **8/11/17/21**、`verifyPlugin`、main 上 draft release | Verifier `ides { recommended() }`，**未**显式跑规格 §22 的 2025.3 + 最新稳定 + 最新 EAP 三件套 |
| `run-ui-tests.yml` | 三 OS 上 `ideaUiTest` | 同上，非 RemoteRobot 真 UI |
| `run-plugin-verifier.yml` | 周更 / 手动 | 同 `recommended()` |
| 性能 / 10 万事件内存 | **无** | — |

### 1.8 文档与遗留

| 项 | 现状 |
| --- | --- |
| README Features | 把 Dump / Timeline / Transformers / Async stack / Drop Frame / 全套 ToolWindow 写成已提供能力，超出 UI 接线 |
| CHANGELOG `[3.0.0]` | 记录 foundation 事实，但「Global JVM queries no longer require hitting a breakpoint」未标明 UI 仅接了 loaders+classes |
| `doc/operation/*.gif` | 2.x 断点 + `System.out` 流程仍在；README 已声明「不是 3.0 协议」 |
| `.gitmodules` + 空目录 `runtime-pivot-java-agent/`、`runtime-pivot-test-demo/` | 仍在；Agent 已 in-tree，子模块未初始化 |
| 迁移说明 | **无** 独立 2.x → 3.0 迁移文档 |

---

## 2. 与 3.0 规格 Phase 0–8 对照

判定：`done` = 规格该阶段任务与验收在源码中基本满足；`partial` = 骨架或一半能力；`missing` = 无有效实现。证据为路径，不是 changelog。

| Phase | 规格要点 | 判定 | 证据 |
| --- | --- | --- | --- |
| **0 基线** | 3.0.0、253、Java 21、Kotlin 2.x、Gradle 9、多模块、Agent 源码 in-tree | **done** | `gradle.properties`、`settings.gradle.kts`、agent 源码 |
| **1 公开 API** | 删除 impl / TestOnly 复制；公开 session/evaluator；异步栈帧；对象改表达式 | **partial** | impl 已清；`ExpressionEvaluation` / `AsyncStackFrames` 已写；栈帧与 Drop Frame **未进 UI**；对象仅 Evaluate |
| **2 ToolWindow** | 五页、session model、替换弹窗、UI 测主要状态 | **partial** | 五页已注册；Probes 占位；Objects/Sessions 不完整；`RuntimePivotSwingComponentTest` 不测真实面板 |
| **3 OpAMP** | proto、loopback WS/HTTP、token、command/progress/cancel、capability、分块 | **partial** | 握手/命令/token/版本 **done**；progress 未发送；cancel 未接线；无分块；无自动重连；EventBatch 只收不发 |
| **4 全局 JVM** | ClassLoader / Loaded / Timeline / Dump / 自有 Transformer；无需断点；有界；禁 sun.instrument | **partial** | Agent 五命令 **done**；UI 只接 loaders + loaded（且无过滤分页）；Dump 不落盘；IT 未覆盖 dump/timeline |
| **5 对象** | Handle、Layout、Store、Load Preview、会话结束释放 | **missing** | 无 proto、无命令、无 handle；仅 JDI Evaluate |
| **6 Probe / Monitoring** | ASM 插桩、nanoTime、Dashboard、Tracepoint Bridge、Pause Interval 分离 | **missing** | `NoOpProbeEngine`；`ProbesPanel` 占位；无 Pause Interval |
| **7 Stack / Drop Frame** | 异步栈、自有 formatter、Drop Frame 隔离、禁内部 API | **partial** | 适配类存在且隔离；UI 未接；无断点 formatter；无 Tracepoint 创建 |
| **8 删遗留** | 删 ActionExecutor 字符串、旧 Dialog、不可追踪 jar、旧配置名、README/CHANGELOG/迁移 | **partial** | 旧实现与 fat jar 已删；设置已改名并迁移 `attachAgent`；README 超前；GIF/submodule/迁移文档仍在 |

先前评审「0–3 done，4 partial，5–6 missing，7 partial，8 mostly」**过于乐观**：Phase 2 与 Phase 3 的验收（UI 状态、progress/cancel、分块、可恢复连接）在源码中未满足，故记 **partial**。

---

## 3. 已知偏差 / 风险（仅核实过的）

1. **Capability 超售**：Agent/Server 都宣称 `objects` / `probes` / `events`，但 objects/probes 无实现，events 无推送。对端会以为可发对应命令。
2. **README 超前宣传**：Features 列表把 Agent 已有、UI 未接、甚至完全未做的能力写成 3.0 特性。
3. **CHANGELOG 完成语气**：`[3.0.0]` 未写明 foundation ≠ 规格完成。
4. **Suspend ALL**：规格 §9.4 要求命中全暂停时提示「恢复后执行」或改 JDI Bridge。UI 与 `SessionLifecycleListener` 均无此逻辑。断点挂起时 Agent IO 线程同样可能停，命令会超时。
5. **HTTP poll 限制**：回退路径 2s 一轮，且 `flushOutgoing` 在非 push sink 上每次只发送一条 `ServerToAgent`。命令、cancel、connection settings 会排队；15s 命令超时在积压时仍可能误伤。
6. **无分块**：Dump 把整份 bytecode 放进单条 CustomMessage。超过 4 MiB 会被 `OpampWire.requireSize` 拒绝。规格允许「后续按 OpAMP 分块扩展」，当前未做。
7. **PathSafety 未接入**：`PathSafety.resolveUnder` 仅有单测；Dump 不写文件；无 output directory 设置。一旦 UI 直接写盘，存在目录穿越窗口。
8. **`SessionLifecycleListener` 空桩**：会话切换/停止不更新 Sessions UI、不计算 Pause Interval、不释放对象 handle（后者也还不存在）。
9. **注入失败静默**：`RuntimeJavaAgentConfig.patchJavaParameters` 与 `OpenRuntimePivotAction` 吞掉异常，用户只看到未连接。
10. **Classes UI 空 payload**：`CLASS_LOADED` 不传 `LoadedClassesRequest`，大 JVM 一次最多 10_000 类且无过滤，容易卡 EDT 解析/填表。
11. **Dump 非可修改类直接失败**：`isModifiableClass==false` 抛错，不返回「只读无法 dump」的结构化降级。
12. **空 gitmodules**：`.gitmodules` 仍指向旧 agent 仓库；空目录会让贡献者以为还要 `git submodule update`。
13. **Verifier 矩阵窄于规格**：`pluginVerification.ides { recommended() }`，不是 §22 的三版本门禁。
14. **Qodana 镜像 2025.1** vs 平台 2025.3（`qodana.yml`）。
15. **规格示例 `platformType=IC`** 与仓库 `intellijIdea()` 不一致——以仓库为准（IC 工件在 2025.3 不再单独发布）。

---

## 4. 目标架构重申与决策表

架构不变：

```text
ToolWindow ──► Project Service (RuntimePivotOpampService)
                 ├─ JDI 控制面（暂停帧、表达式、断点、Drop Frame）
                 └─ Agent 数据面（OpAMP Server ← loopback ← Agent Client）
```

非目标沿用规格 §4：不是生产 APM；不远程主机；不用内部 API 保旧交互；不默认开放任意代码远程执行；不用 JDI MethodEntry/Exit 做高频追踪；不兼容 2025.2−。

### 4.1 核实后的决策表（冲突时以本表为准）

| ID | 决策 | 理由 |
| --- | --- | --- |
| D1 | **未实现的 custom capability 不得出现在 handshake 集合中**。当前应只协商 `io.runtime.pivot.v1` + `classes`；`events` 在真正推送 `EventBatch` 后再加；`objects` / `probes` 随 Wave 2/3 再加。未知命令继续 `UNSUPPORTED`。 | 避免对端误判 |
| D2 | README / Marketplace 描述只陈述 **UI 已接线且 Agent 已实现** 的交集。Agent-only 能力标「协议已支持、UI 未接」。 | 规格 §23「与真实行为一致」 |
| D3 | 平台工件保持 `intellijIdea()`，不改回 `platformType=IC`。 | 2025.3 统一制品 |
| D4 | 动态 Attach 仍为可选；`agentmain` 可保留。3.0 完成 **不** 依赖 Attach UX。无 `jdk.attach` 时不得伪装成功。 | 规格 §9.2 |
| D5 | Dump 默认精确类名；多 loader 必须用户选择；落盘必须 `PathSafety`。 | 规格 §16.3 |
| D6 | Transformer 只展示 Runtime Pivot 自己登记的项。禁止恢复 `sun.instrument` 枚举。 | 规格 §13.1 |
| D7 | 对象交互走表达式 + Evaluator，禁止 debugger-tree 内部节点。 | 规格 §13.2 |
| D8 | HTTP 回退保持 2s poll；Wave 内可改为「有 outbound 时立即等下一 poll」，但不得把 poll 重新拉到心跳 30s。分块与多消息 HTTP flush 作为架构优化，不阻塞 Wave 1。 | PR #6 已修 30s 问题 |
| D9 | `CommandProgress` / UI 取消属于 OpAMP 保真，Wave 1 Dump 若可能 >1s 应接取消；完整进度条可与分块一起做。 | 规格 §10.3 / §15.6 |
| D10 | Pause Interval 与 Probe 计时分离；禁止再把墙钟 resume→pause 叫 Monitoring。 | 规格 §11.3 |
| D11 | Experimental `XDropFrameHandler` 只能留在 `DropFrameCapability`。 | 规格 §6.2 |
| D12 | 后续 PR **按 Wave 拆分**；一 PR 不跨 Wave 塞功能。 | 可审、可回滚 |

---

## 5. 分阶段落地计划

顺序固定：诚实化 → 已有 Agent 命令的 UI → 对象 → Probe → Sessions 控制面 → 设置/安全/清理 → 测试门禁。

### Wave 0 — 诚实化

**目标：** 对外描述、capability、错误码与仓库真实能力对齐。不增加功能。

**范围：**

- `PivotCapabilities`：handshake 只声明已实现集合（D1）。
- README Features：按「已接线 / 协议已支持 UI 未接 / 未做」三档改写；保留 OpAMP 安全描述中已核实部分。
- CHANGELOG：标明 3.0.0 为 OpAMP foundation；未完成项指向本文。
- 对未注册命令保持 `ERROR_UNSUPPORTED`（已有）；补一条协议测试：未注册命令返回该码。
- Probes / Objects 面板在 capability 缺失时显示「当前 Agent 未提供该能力」，而不是像已上线。

**非范围：** 任何新命令、新 UI 控件、capability 重新加回。

**关键设计决策：** 先收缩宣称，再在后续 Wave 加回。不要用文档注释代替 handshake 收缩。

**验收：**

- 新 Agent 与 Server 协商集合不含 `objects` / `probes`；`events` 在无 EventBatch 发布器时不含。
- README 不再把 Dump/Timeline/Drop Frame 写成已提供 UI。
- `PivotCapabilitiesTest` 断言收缩后的集合。

**建议测试：** 扩展 `PivotCapabilitiesTest`、握手测试断言 `negotiated` 集合；一条 UNSUPPORTED 命令集成断言。

**模块：** `protocol`，`README.md`，`CHANGELOG.md`，必要时 `ProbesPanel` / `ObjectsPanel` 文案。

---

### Wave 1 — Classes UI 补齐

**目标：** 把 Agent 已有的 class dump / timeline / transformers / 过滤分页接到 Classes 页。无需断点。

**范围：**

- Classes：ClassLoader 树选择 → 过滤 prefix / loader / limit；表格展示 `truncated`。
- Dump：精确类名 + loader 选择；结果展示 SHA-256 / 是否可修改；可选保存（若本波落盘，必须 `PathSafety` + 输出根目录；否则先内存/Console/临时 VirtualFile）。
- Timeline：拉取 `class.loading.timeline`，展示 `dropped_events`。
- Transformers：表格 + retransform/redefine 标志。
- 未连接禁用按钮；失败打印 `CommandError` 码；长请求可取消（调用已有 `cancel`）。
- 补 `AgentPremainIT`：dump（精确名）、timeline、错误类名、多 loader 需 `loader_id`。

**非范围：** EventBatch 实时推送（属架构优化）；对象/Probe；分页控件的精美程度不挡功能。

**关键设计决策：** UI 必须发 `LoadedClassesRequest` / `ClassDumpRequest` proto，禁止再发空 payload。Dump 类名禁止 `contains` 匹配（Agent 已是 `equals`）。

**验收：** 运行中无断点可完成 loaders → classes（过滤）→ dump → timeline → transformers。大结果有 truncated 提示。`sun.instrument` 扫描仍为 0。

**建议测试：** 扩展 `AgentPremainIT`；Classes 面板 headless 测试（有/无连接）；dump 超限消息失败路径（可用小 `maxMessageBytes`）。

**模块：** `plugin-ui`，`plugin-core`，`integration-tests`，必要时 `protocol`（不改字段号）。

---

### Wave 2 — Object Handle / Layout / Store / Load Preview（规格 Phase 5）

**目标：** 在暂停帧表达式求值之上建立对象数据面，且不恢复 debugger-tree 内部节点。

**范围：**

- proto：`ObjectHandle`、`ObjectLayoutResult`、`ObjectSnapshotResult`、`ObjectMutationPreview` 及对应命令。
- Agent：弱引用 handle（sessionId、TTL、类型摘要）；显式 pin 才强引用；会话/Agent 关闭释放。
- Layout / Store（深度、对象数、文件大小上限、循环引用、不默序列化敏感字段）。
- Load：解析 → 类型校验 → preview → 确认 → 修改；失败不得先 `clear` Collection/Map。
- UI：表达式（可预填编辑器选区）、Evaluate、Layout、Store、Load Preview、确认框（目标 JVM / 表达式 / 影响范围）。
- capability：实现后才把 `objects` 加回 handshake（D1）。

**非范围：** 变量树右键选中 XValue；任意远程类方法反射执行。

**关键设计决策：** JDI 只负责拿到当前帧的对象身份/表达式；布局与序列化在 Agent。Suspend ALL 时走「恢复后执行」或一次性 JDI Bridge，不得假装 OpAMP 一定可达。

**验收：** 规格 Phase 5 验收四条；取消文件选择无 NPE；无手写 Java 源码拼接。

**建议测试：** handle 弱引用与释放；Load preview 失败不 mutate；PathSafety 写盘；无会话 / 未暂停。

**模块：** `protocol`，`agent-core`，`plugin-debugger`，`plugin-ui`，`plugin-core`。

---

### Wave 3 — ASM Probe Engine + Dashboard + Pause Interval 分离（规格 Phase 6 + §11.3）

**目标：** 目标 JVM 内 `System.nanoTime()` 计时；IDEA 墙钟 Pause Interval 单独展示，不再叫 Monitoring。

**范围：**

- `agent-probe`：ASM（或同等成熟库）插桩；kind：至少 `COUNTER`、`TIMER_START`/`TIMER_END`、`METHOD_DURATION`；正常返回与异常退出；有界事件 + 采样；业务线程不写网络。
- shade 增加 ASM relocate。
- Dashboard：count/min/max/avg/p50/p95/p99；启停；失败原因；可选 CSV/JSON。
- Debugger Tracepoint Bridge：公开 `XLineBreakpoint` + `SuspendPolicy.NONE`；文案不得写「零暂停」。
- Sessions 或独立处：**Debug Pause Intervals**（resume→pause 墙钟，标注含 JDWP 开销）。
- capability：实现后才加回 `probes`。

**非范围：** 生产 APM；JDI MethodEntry 高频追踪；在 Probe 中保存任意 Java 源码。

**关键设计决策：** `NoOpProbeEngine` 可保留为测试双；生产 `AgentStarter` 改真实 engine。旧 `XSessionMonitoringDialog` 不得复活。

**验收：** 规格 Phase 6 四条；Pause Interval 与 Probe 在 UI 上名称分离（D10）。

**建议测试：** ProbeDefinition 已有；补插桩后 timer/counter；禁用后停止采集；高频采样背压。

**模块：** `agent-probe`，`agent-bootstrap`（shade），`protocol`，`plugin-ui`，`plugin-debugger`。

---

### Wave 4 — Sessions 接线

**目标：** 把已有调试适配接到 Sessions，并处理 Suspend ALL UX。

**范围：**

- 当前 `XDebugSession` 列表/切换。
- `AsyncStackFrames.compute` 展示栈；EDT 只更新 UI。
- `DropFrameCapability`：不可用时禁用按钮。
- Suspend ALL：Agent 命令入口提示「目标可能无响应，恢复后重试」。
- 自有栈断点 formatter（若本波做 Tracepoint 列表）；不引入 `XBreakpointUtil` impl。

**非范围：** 恢复 2.x 模态大 Dialog 作为主界面。

**关键设计决策：** 继续只用公开 XDebugger API；Drop Frame 不扩散出 `DropFrameCapability`（D11）。

**验收：** 规格 Phase 7 三条；无会话 / 未暂停 / 不支持 Drop Frame 三种 UI 状态可测。

**建议测试：** `AsyncStackFrames` 在无 session/未暂停时的错误回调；Drop Frame `ThreeState` 非 YES 时不调用 `drop`。

**模块：** `plugin-ui`，`plugin-debugger`。

---

### Wave 5 — 设置、PathSafety、确认框、迁移文档、遗留清理

**目标：** 规格 §18–19 与 Phase 8 剩余项。

**范围：**

- 设置：Enable Agent communication、event buffer、probe 默认 sample rate、object snapshot limits、output directory、keep history；变更若需重启 JVM，提示「下次启动生效」（已有注入提示可复用）。
- Run Configuration Override（use default / enable / disable）若公开 API 允许；否则 ADR 说明推迟。
- 所有落盘走 `PathSafety.resolveUnder`。
- 危险操作确认：Object Load、字段修改、自定义代码。
- 2.x → 3.0 迁移文档：菜单入口对照、GIF 作废、Attach 改 Inject、不再 `System.out`。
- 删除或归档 `doc/operation/*.gif`（或移到 `doc/legacy/2.x/` 并在迁移文引用）。
- 删除 `.gitmodules` 与空 submodule 目录（确认无历史需要后）。
- 注入失败不再完全静默：至少 Console/通知一条原因（仍不阻断用户程序启动）。

**非范围：** 新数据面功能。

**验收：** 规格 Phase 8 三条中文档与遗留部分；路径穿越单测仍绿且生产调用点存在。

**建议测试：** Configurable 读写新字段；legacy `attachAgent=false` 迁移保持（已有单测）。

**模块：** `plugin-core`，`plugin-ui`，`protocol`（PathSafety 调用点），`doc/`，`.gitmodules`。

---

### Wave 6 — 测试加厚与发布门禁

**目标：** 使「宣称 3.0 完成」在 CI 上可重复证明。

**范围：**

- Plugin Verifier：显式 2025.3 最低 + 最新稳定 + 最新 EAP；失败级别保持现有 INTERNAL/DEPRECATED 等。
- `AgentPremainIT`：dump、timeline、object snapshot、probe timer/counter、shutdown；矩阵保持 8/11/17/21。
- 真 UI 状态：连接中 / 已连接 / 断开 / 无 Agent / 无会话 / 运行中 / Suspend THREAD / Suspend ALL / 长任务取消 / 大结果分页——至少用 headless 真面板或 fixture，而不是空 `JTabbedPane`。
- 性能：空闲 CPU 近 0；未启用 Probe 无额外业务字节码；10 万类加载事件内存不线性涨（可基准测试，阈值写入 CI 或文档）。
- 可选：agentmain 冒烟（不作为 3.0 必达，见 D4）。
- Qodana 镜像与平台版本对齐（或文档说明为何停留 2025.1）。

**非范围：** 新功能。

**验收：** 规格 §21–23 中仍开放的测试项关闭或显式豁免并写入本文修订。

**建议测试：** 见上。发布前人工归档 Verifier 报告。

**模块：** `integration-tests`，`plugin` 测试，`.github/workflows`，`plugin/build.gradle.kts`。

---

## 6. 架构优化项（可与 Wave 并行的独立 PR，但不得破坏门禁）

| 项 | 现状 | 建议 |
| --- | --- | --- |
| OpAMP progress | proto 有，`CustomMessages` 无编码器，Server 忽略 | 增加 `commandProgress`；Server 转 UI；长 dump/store 上报 percent |
| OpAMP cancel | 协议+`CommandContext` 有，dump 循环不检查，UI 无按钮 | handler 热点 `throwIfCancelled`；Classes/Objects 取消 |
| Chunking | 单消息 4 MiB 上限 | 按 OpAMP 分块或 CustomMessage 片；**禁止**换行帧 |
| HTTP multi-flush | 每 poll 一条 outbound | HTTP 响应可冲队列或短间隔 poll-on-demand |
| EventBatch 推送 | 只收不发；EVENTS capability 超售 | 有界队列 drain → Agent `sendCustom`；UI Timeline 增量；然后才加回 `events` capability |
| 自动重连 | Client `onClose` 不重连 | 指数退避重连同一 token/session，或明确要求重启目标 JVM |
| 模块边界 | plugin-ui 直接调 `OpampServer.AgentSession` | UI 只依赖 `RuntimePivotOpampService` 快照 DTO |
| shade / ASM | 现 shade protobuf/ws/slf4j | Wave 3 增加 ASM relocate；bootstrap 保持最小 |
| 类查询性能 | 每次 `getAllLoadedClasses()` 全扫 | loader/name 索引；弱引用缓存；与 ClassLoadingRecorder 同源 id |
| 安全 | token 脱敏已做；落盘未接 PathSafety | Wave 5；错误栈限制已有 8 帧，保持不回传对象内容 |
| Isolated CL | `isAgentClass` 仍列出未 shade 的 `com.google.protobuf` 等名 | 与 shadow relocate 对齐，避免双路径加载 |
| `JavaProgramPatcher` 异常 | 吞掉 | Wave 5 可观测性 |

这些优化 **不是** 绕过 Wave 0 诚实化的借口：未做完之前仍不得在 handshake/README 里写成已交付。

---

## 7. 完成定义（对照规格 §23）

只有下表全部为「满足」才能把产品表述从「3.0 foundation」改为「3.0 完成」。`pluginVersion=3.0.0` 已经占用，完成时用 CHANGELOG / Marketplace 文案区分，**不要**靠再改一个 SemVer 来回避诚实描述（若需 3.0.1 只表示 foundation 后的修复/增量，须在 CHANGELOG 写清）。

| §23 条件 | 当前 | 何时满足 |
| --- | --- | --- |
| 最低 IDEA 2025.3 / 253 | 满足 | Phase 0 |
| 无 `com.intellij.*.impl` import | 满足 | Phase 1 已清 |
| 不反射 IDEA/JDK 私有 API | 满足（生产扫描） | 保持；Agent 隔离 CL 的 `Class.forName(AgentStarter)` 不是 IDEA API |
| Verifier 无 Internal / compatibility problem | CI 有 `verifyPlugin` + INTERNAL 失败级别 | Wave 6 补齐版本矩阵后维持 |
| 核心结果结构化 DTO | class/ping **满足**；object/probe **不满足** | Wave 2–3 |
| 全局 JVM 功能无需断点 | Agent **满足**；UI 仅 loaders+classes | Wave 1 |
| 局部变量仍走暂停帧 | Evaluate **满足** | 保持 |
| Monitoring 用目标 JVM 计时 | **不满足**（NoOp） | Wave 3 |
| ToolWindow 覆盖核心工作流 | 骨架 **部分** | Wave 1–4 |
| loopback + token + 版本 + 能力协商 | 传输 **满足**；能力集合 **超售** | Wave 0 收缩 + 后续按能力加回 |
| 类加载事件与 object handle 无无限强引用 | 类加载 **满足**；handle **不存在** | Wave 2 |
| Object Load 失败不破坏原对象 | **不满足**（无 Load） | Wave 2 |
| 测试不是 `NO-SOURCE` | 满足（已有分层测试） | Wave 6 加厚到规格 §21 |
| README 与迁移文档与真实行为一致 | **不满足** | Wave 0 + Wave 5 |

---

## 8. 执行约定

1. 后续实现 PR **按 Wave 0→6 拆分**；允许 Wave 内再拆小 PR，不允许把 Wave 2 对象和 Wave 3 Probe 塞进同一 PR。
2. 每个 PR 先对照本文 §1 再改代码，不得在旧 2.x 架构或已删除的 `ActionExecutor` 字符串协议上加功能。
3. **不绕过** 公开 API 门禁；**不** 监听非 loopback、不固定端口、不取消 token；**不** 恢复 debugger-tree 内部节点；**不** 枚举第三方 Transformer。
4. 遇到公开 API 不足：缩小交互，写进决策表修订，而不是用 `impl`。
5. 每波带上对应测试，不把测试留到 Wave 6 一次性补（Wave 6 只加厚门禁）。
6. 不修改与该 Wave 无关的文件。
7. 用户自行 @ AI review（例如 Codex）。**本文档作者与后续代理不要在 PR 正文里 @ 审查 bot**；审查由维护者触发。

规格 §24 第 7 条「不直接推送 GitHub」适用于当时的本地代理约束；本仓库的 Cloud Agent 工作流以维护者配置为准，但 **不得 merge 本文未经维护者确认的 PR**。

---

## 9. 附录：核实范围与方法

- 读取规格 `doc/runtime-pivot-3.0-refactoring-plan.md`（OpAMP 版）。
- Fast-forward `main` 到 PR #6 merge commit `f43a23c` 后通读 `protocol/`、`agent/`、`plugin/`、`integration-tests/`、`test-apps/`、CI、`README.md`、`CHANGELOG.md`、`gradle.properties`。
- 以类名、命令常量和 proto 消息为准；changelog 只作旁证。
- 未在本环境跑全量 `./gradlew check`（文档 PR 不改生产逻辑）。测试结论来自测试源码内容，而非本次执行绿结果。
