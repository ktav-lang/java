# Changelog

**Languages:** [English](../../CHANGELOG.md) · [Русский](../ru/CHANGELOG.ru.md) · **简体中文**

Java 绑定的所有显著变更记录于此。格式基于
[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)；版本号遵循
[Semantic Versioning](https://semver.org/)，采用 pre-1.0 约定：
MINOR 递增视为破坏性变更。

本 changelog 跟踪 **绑定发布**，不覆盖 Ktav 格式自身的变更 ——
后者见 [`ktav-lang/spec`](https://github.com/ktav-lang/spec/blob/main/CHANGELOG.md)。

## Unreleased

## 0.8.0 — 2026-09-24

### 新增

- **`Ktav.format(String src)`** —— 保留注释的格式化器，作用于 Ktav
  *源文本*，而非 `Value` 渲染器。它将文档的结构性写法归一化为规范形式
  （§ 5.9），同时保留规范 writer 会丢弃的 trivia。每条注释都逐字保留；
  Ktav 没有行尾注释（§ 3.4：注释独占整行），因此归属毫无歧义。空行作为
  分组提示予以保留，但连续两个及以上会折叠为恰好一个，而紧贴括号内部的
  空行会被丢弃 —— 这正是使该转换成为不动点的原因。键顺序永不改变
  （规范形式没有排序规则）。对于既无注释*也无空行*的文档，结果等于
  `Ktav.emitCanonical(Ktav.loads(src))`；这个更强的条件是刻意为之，
  因为空行之于 `Value` 模型，并不比注释更有资格存在。

  ```java
  Ktav.format("## the server\nserver: {host: a, port: 80}\n");
  // ## the server
  // server: {
  //     host: a
  //     port: 80
  // }
  ```

- **`KtavException` 现在携带九个结构化错误字段**，来自 Rust 核心的
  error envelope：`getError()`、`getReason()`、`getLine()`、
  `getLineText()`、`getSpanStart()`、`getSpanEnd()`、`getPath()`、
  `getBody()`、`getCanonical()`、`getSpecSection()`。
  缺失的信息为 `null` —— 这正是数值访问器返回装箱 `Long` 而非 `long`
  的原因 —— 而绝不会是缺失的访问器。

  `getPath()` 返回 `List<String>`,内容是**精确解码后的键段，
  而非拼接字符串**：字面名为 `a.b` 的键是单个段，不会与两段路径混淆。

  ```java
  try {
      Ktav.loadsStrict("version: 1.10\n");
  } catch (KtavException e) {
      e.getError();        // "LossyScalar"
      e.getBody();         // "1.10"
      e.getCanonical();    // "1.1"
      e.getSpecSection();  // "§3.6/§5.2"
  }
  ```

  信封区分了 writer 的两种拒绝：`"UnrepresentableAt"` —— 当 writer
  能指出问题节点所在时（此时它还会填充 `getPath()`）；
  `"Unrepresentable"` —— 当不能时。两者的 `reason` 码相同，因此只需
  判断“写入被拒绝”的调用方匹配 `getReason()` 即可。

  信封由永不抛异常的 streaming reader 解析：无法识别的载荷会降级为
  `Message` 类错误，而不是把一次诊断变成第二次失败。

- **`Ktav.canonicalFromSource(String src)`** —— 在单个原生调用中解析
  Ktav 源文本并重新输出为规范形式：相当于 `emitCanonical(loads(src))`，
  但没有中间的 `Value` 树及其 JSON-wire 编解码往返。两个入口逐字节一致
  —— 已在 bigint 整数、巨大指数、`-0.0` 与长尾数 float 上验证，文本
  形式的存储让任何分歧都无处产生。与 `emitCanonical` 本身一样，注释与
  空行不会被保留。

### 变更

- **错误消息现在是核心自己的渲染。** `getMessage()` 逐字返回信封的
  `message` 字段 —— 与 Rust crate 及所有其他绑定对同一错误打印的文本
  相同 —— 而不是本地重新拼装的句子，也不是原始 JSON。对于不写入
  `message` 字段的 0.8.0 之前原生库，绑定回退到自己的重构。依赖消息
  字符串匹配的调用方应改为匹配 `getError()` / `getReason()` ——
  这正是本次改动的目的。

- **中间的 0.7 周期从未发布。** 0.7.0/0.7.1 只是内部里程碑（跟踪
  ktav 0.7、`format_str`、`ErrorEnvelope`），并被本发布取代；依赖
  下限与 spec 固定版本从 0.6.4 直接跃迁到 **0.8.0**。不存在受支持的
  0.7.x 绑定版本，也不存在 v0.7.0 的 spec 固定。

- `crates/cabi` 改为单次调用 `ktav::declare_cabi!()`（ktav 的 `cabi`
  特性），取代手写的 C ABI 垫片；导出的符号集不变，因此 Java API
  不受影响。0.8.0 依赖下限和 `v0.8.0` spec 固定版本带来 § 5.2：
  带有多余前导零的十进制数解析为 String，而非 Integer。
- 包版本升至 **0.8.0**，与核心和规范同步；预编译库的回退下载现在指向
  `v0.8.0` 发布资产。
- Conformance 运行器读取 `spec/versions/0.8/tests`(子模块重新固定到
  `0.8.0` 之后，它一直静默读取过期的 `0.7` 语料——路径是硬编码的，
  并非从固定版本推导而来)，并执行语料中的每个类别，包括新增的
  `strict-lossy/`（`loads()` 必须等于 lax 值，`loadsStrict()` 必须以
  匹配的原因、body 与规范形式抛出异常）。一个 guard 测试会在语料中
  出现无法识别的类别目录时使构建失败，以防止这个问题再次悄然发生。
- 从未发布的 0.7 周期继承而来（历史 —— 在同一窗口内已被 0.8.0 固定
  取代）：`rust-version` 提升至 `1.71`（ktav 0.7 的 MSRV）；运行器
  开始执行 `unrepresentable/` 与 `parseable-unrepresentable/` 类别；
  `emitCanonical` 输出与每个 valid fixture 的 `.canonical.ktav`
  伴随文件逐字节比对（spec § 5.9.10、§ 5.9.8）；语料库填充度 guard
  开始覆盖所有 fixture 类别。

- 文档：README 的简介与快速开始不再把 Maven Central 发布描述为
  “已规划” —— release workflow 会在每个 tag 发布
  `io.github.ktav-lang:ktav`。键转义示例不再使用行尾 `//` 注释 ——
  这不是 Ktav 的语法（注释是独占一行的 `##`，§ 3.4）。`examples/basic`
  已重写，去掉了已移除的 `:i` / `:f` 类型标记；`Value` 的 Javadoc
  描述的是从词法形式推断数字类型，而非标记语法。

### 修复

- **`ScalarRoot` 与 `NonFiniteFloat` 现在会透出其规范的 § 5.9.0 原因码。**
  这两种 unrepresentable 值此前根本到不了原生 writer —— JSON-wire 边界
  会先把它们当作不透明的 `Message` 类错误拒绝 —— 因此 `getReason()`
  返回 `null`，conformance 运行器也把这两种原因排除在断言之外。绑定
  现在自行拒绝它们，并给出与核心 writer 相同的信封：`error`
  `UnrepresentableAt`、规范原因、指向问题节点的解码键 `path`
  （`ScalarRoot` 时为空）、`spec_section` `§ 5.9.0`，以及核心自己的
  消息文本。`toStringForceStrings` 仍然把非有限 float 强制为 String，
  而不是拒绝。
- Conformance 运行器实现了 § 8.5 运行器契约：加载 `manifest.json`，
  拒绝一切它未实现的 schema 版本，强制执行封闭的类别集合与精确的
  fixture 数量，并遵循 `raw_bytes` 标志 —— invalid-UTF-8 fixture 在
  严格的字节->String 边界上对照其 oracle 的 `InvalidUtf8` 预期进行
  校验。Invalid fixture 现在会比较 JSON oracle 中的 `expected_error`，
  而不是只断言“抛出了某个异常”。缺失 `ktav_cabi` 或 spec 子模块时，
  语料测试会失败，而不是发出伪装通过的 skip 测试。

## 0.6.4 — 2026-08-23

### 新增

- **`Ktav.loadsStrict(String)`** —— 通过 Java API 和 `ktav_loads_strict`
  JNA/C ABI 符号暴露 strict numeric parser。

### 变更

- 跟踪 `ktav 0.6.4` 与 spec 0.6.4，包括规范化 float 边界和
  `notation_boundaries` fixture。
- 原生库加载器现在指向精确的 `v0.6.4` release asset。

## [0.6.1] — 2026-06-05

- 文档：将所有 README 示例改写为 spec 0.6 语法（裸数字替代已移除的 `:i`/`:f` 标记；`##` 注释替代 `#`）。

## 0.6.0 — 2026-06-01

同步至 Ktav 0.6.0 —— 键现在支持转义。

### 新增

- 键处理完整的 §3.7 转义集合，并新增两个转义：
  - `\.` → `.`（字面量点 —— **不**会切分 dotted-path）
  - `\:` → `:`（字面量冒号 —— **不**作为键/值分隔符）
- 示例：`a\.b: v` → `{"a.b": "v"}`，`a\:b: v` → `{"a:b": "v"}`，
  `x.y\.z: v` → `{"x": {"y.z": "v"}}`。

### 破坏性变更

- 键中的字面量反斜杠现在需要写作 `\`（此前键中的 `` 是普通字节）。
  实际中很少出现；按 pre-1.0 SemVer 为 MINOR bump。

### 变更

- 跟踪 ktav-rust 0.6.0 / Ktav 规范 0.6.0。绑定源码未改动 —— escape
  语义的变化完全在 Rust 内核中实现，JNA 边界对其透明。

---

## 0.5.0 — 2026-05-27

实现 Ktav spec 0.5.0。跟踪 ktav-rust 0.5.0。

### 破坏性变更

- 移除了类型标记 `:i` / `:f`。数字、布尔值与 `null` 依据词法形式推断
  （spec §§ 3.6、5.2）。写 `key: 42` 得 Integer，写 `key: 3.14` 得
  Float，写 `key:: 42` 保持 String。
- 注释现在使用 `##`（独占一行）。单个 `#` 字节是内容，而非注释。
- 裸整数与浮点数不再解析为 String —— `port: 8080` 得到的是
  `Value.Int("8080")`，而非 `Value.Str("8080")`。
- 首个内容行上单独的 `{` / `[` 打开多行根 Object / Array；0.1.1 的
  JSONL 式语义已移除。
- 键段会裁剪首尾空白。

### 新增

- **内联复合值** `{k: v, …}` / `[i, …]`（spec § 5.8），支持尾随逗号、
  值中部花括号字面量（§ 5.8.5）以及 128 的嵌套深度上限。
- 内联标量中的**八个转义序列**：`\`、`\,`、`\}`、`\]`、`\{`、`\[`、
  `\n`、`\r`（spec § 3.7）。
- **`Ktav.emitCanonical(Value)`** —— 将 Value 渲染为确定性的规范
  Ktav 形式（spec § 7），经由新增的 `ktav_emit_canonical` C ABI 导出。

### 变更

- 许可证：MIT → MIT OR Apache-2.0（`LICENSE-MIT` + `LICENSE-APACHE`）。
- spec 子模块：v0.5.0。
- ktav-rust 依赖：0.5.0。
- 一致性测试现在针对 `spec/versions/0.5/tests/` 运行。

## 0.3.1 — 2026-05-10

### 新增

- **顶层 Array 支持** (spec § 5.0.1, ktav 0.3.1) —— 首行内容决定解析为
  Object 还是 Array。当文档以数组项形态开头(裸标量、类型化/raw 标记、
  单独的开始符、多行开始符)时,`Ktav.loads` 现在返回 `Value.Arr`;
  所有此前在 0.3.0 下有效的文档仍解析为相同的 `Value`。`Ktav.dumps`
  现在也接受顶层的 `Value.Arr`。
- **`Ktav.toStringForceStrings(Value)`** —— 渲染任意 Value,并将每个
  叶子标量(类型化整数 `:i`、类型化浮点 `:f`、布尔值与 `null`)通过
  raw 标记 (`::`) 强制转换为 String。复合值保持结构不变。适用于
  "一切皆字符串" 的转储。通过 `loads` 往返后仍得到同一组 String
  标量。

### 变更

- **已采用 `ktav 0.3.1`** —— 跟踪 ktav 0.3.1(增量变更)。spec 子模块
  已同步至 `7256816` (spec 0.1.1)。

### 原生二进制缓存

- `NativeLoader.LIB_VERSION` 已升至 `0.3.1`。升级后首次调用时从
  匹配的 GitHub Release 重新下载。

## 0.3.0 — 2026-05-08

### 变更

- **已采用 `ktav 0.3.0`** —— 跟踪 ktav 0.3.0。spec 子模块已同步至
  `46d94a7` (收紧括号字符串 fixture 的处理)。

## 0.2.0 — 2026-05-07

### 变更（破坏性）

- **已采用 `ktav 0.2.0`** —— 多行字符串现在默认序列化为缩进的
  stripped `( ... )` 形式。`:f 42` 现在接受整数字面量(解析为
  `42.0`)。参见 [`ktav` crate CHANGELOG](https://github.com/ktav-lang/rust/blob/main/CHANGELOG.md#020--2026-05-07)。

  需要更新将序列化输出与内置 `((...))` 字面量逐字节比较的代码。
  往返转换不变。

### 规范

- spec 子模块已同步(fixture typed_float_integer_body;预期值 42.0)。

## 0.1.3 — 2026-05-03

### 变更

- **已采用 `ktav 0.1.5`** —— 上游 Rust crate 引入了结构化错误 API
  (`Error::Structured(ErrorKind)` 带字节偏移 span)、对错误枚举追溯
  应用了 `#[non_exhaustive]`,以及公开的事件式解析器 `ktav::thin`。
  Java 绑定对用户可见的行为没有变化:`KtavException` 仍携带相同的
  人类可读消息(七个标准类别的 Display 字符串与 ktav 0.1.4 完全
  字节相同,由 ktav 自己的 pinning 测试验证)。将 `ktav::ErrorKind`
  映射到结构化 Java 异常层级(`KtavMissingSeparatorSpaceException`、
  `KtavDuplicateKeyException` 等)是单独的后续工作,记录在
  [`STRUCTURED_ERRORS.md`](https://github.com/ktav-lang/.github/blob/main/STRUCTURED_ERRORS.md)。

Maven Central:`io.github.ktav-lang:ktav:0.1.3`。

## 0.1.2 — 2026-04-26

### 变更

- **升级到 `ktav 0.1.4`** —— 上游 Rust crate 中 `cabi` 使用的 untyped
  `parse() → Value` 路径,小文档加速约 30%、大文档加速约 13%,只是
  `Frame::Object` 的初始容量微调(4 → 8)。每次 `Ktav.loads` 都会
  透明地受益。

Maven 坐标:`io.github.ktav-lang:ktav:0.1.2`。依赖声明:

```kotlin
implementation("io.github.ktav-lang:ktav:0.1.2")
```

## 0.1.1 — Maven Central + review fixes

首次发布到 Maven Central:`io.github.ktav-lang:ktav:0.1.1`。
依赖声明:

```kotlin
implementation("io.github.ktav-lang:ktav:0.1.1")
```

### 修复

- `Ktav.callNative` 用 try-with-resources 包裹 JNA `Memory`
  ——即使 FFI 调用抛出异常,native 缓冲也会被释放
  (之前会泄漏到下一次 GC finalizer)。
- `Value.Obj` / `Value.Arr` 在 compact 构造器中做防御性拷贝并
  以不可变视图暴露 —— 这两个 record 自称不可变,但通过访问器
  调用方可以变更内容。
- `NativeLoader.download` 在 rename 之前通过 `WRITE` 打开的
  `FileChannel` 对 body 字节执行 `fsync`,关闭崩溃后缓存损坏的窗口。
- `NativeLoader.testOverride` 改为 `volatile`,
  以确保 JUnit 并行执行下的可见性。
- `ConformanceTest` 在交回 `@TestFactory` 之前通过 try-with-
  resources 关闭 `Files.walk` 流,修复目录迭代器句柄泄漏
  (在 Windows 上表现为 `FileSystemException`)。
- `SmokeTest.roundTripSimpleDocument` 现在校验它写入的每个键
  (`ratio`、`nested.inner` 原本未被断言)。

## 0.1.0 — first public release

首次发布。目标格式版本:**Ktav 0.1**。

### 构件坐标

group/name:`io.github.ktav-lang:ktav`。Maven Central 发布 ——
已规划;在此之前 JAR 作为 GitHub Release 资产分发。

### 公共 API

- `Ktav.loads(String) -> Value` —— 解析 Ktav 文档。
- `Ktav.dumps(Value) -> String` —— 将 `Value` 渲染为 Ktav 文本。
- `Ktav.nativeVersion() -> String` —— 已加载 `ktav_cabi` 的版本。
- `KtavException` —— 解析/渲染错误,消息来自原生侧。
- `Value` —— 七变体的 sealed 接口 (`Null`、`Bool`、`Int`、`Flt`、
  `Str`、`Arr`、`Obj`),与 Rust crate 的 `Value` 枚举一一对应。

### 架构

- **原生核心** —— 参考 Rust crate `ktav`,通过极简的 `extern "C"` C
  ABI (`crates/cabi`) 封装,分发为预编译的 `.so` / `.dylib` / `.dll`。
- **Java 加载器** —— JNA(使用方无需 JNI 编译):库在首次调用时
  从 `$KTAV_LIB_PATH` 解析,或从对应的 GitHub Release 资产一次性
  下载到用户缓存。
- **Wire 格式** —— Rust 与 Java 之间使用 JSON,带有
  `{"$i":"..."}` / `{"$f":"..."}` 标记包装,实现带类型的
  整数/浮点无损往返及任意精度整数 (`BigInteger`)。

### 类型映射

|| Ktav             | `Value` 变体                                             |
|| ---------------- | ------------------------------------------------------- |
|| `null`           | `Value.Null.NULL`                                       |
|| `true` / `false` | `Value.Bool`                                            |
|| `:i <digits>`    | `Value.Int`(文本形式 —— 任意精度)                      |
|| `:f <number>`    | `Value.Flt`(文本形式 —— 精确往返)                      |
|| 裸 scalar        | `Value.Str`                                             |
|| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
|| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`)            |

### 平台

预编译原生二进制覆盖:

- `linux/amd64`、`linux/arm64`(glibc)
- `darwin/amd64`、`darwin/arm64`
- `windows/amd64`、`windows/arm64`

Alpine(musl) —— 计划在后续版本加入。

### 测试覆盖

在 JDK 17 / 21 × Linux / macOS / Windows 上运行完整的
Ktav 0.1 conformance 套件(所有 `valid/` 与 `invalid/` fixture)。

### 致谢

基于参考 Rust crate `ktav` 构建。动态加载通过
[JNA](https://github.com/java-native-access/jna)。Streaming JSON
通过 [Jackson](https://github.com/FasterXML/jackson-core)。
