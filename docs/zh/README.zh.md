# ktav — Java 绑定

[![Maven Central](https://img.shields.io/maven-central/v/io.github.ktav-lang/ktav?style=flat-square&logo=apachemaven&logoColor=white&label=Maven%20Central)](https://central.sonatype.com/artifact/io.github.ktav-lang/ktav)
[![CI](https://img.shields.io/github/actions/workflow/status/ktav-lang/java/ci.yml?style=flat-square&logo=github&label=CI)](https://github.com/ktav-lang/java/actions)
![License: MIT OR Apache-2.0](https://img.shields.io/badge/license-MIT%20OR%20Apache--2.0-blue?style=flat-square)
[![Playground](https://img.shields.io/badge/playground-try%20online-7c3aed?style=flat-square&logo=rocket&logoColor=white)](https://ktav-lang.github.io/)

**Languages:** [English](../../README.md) · [Русский](../ru/README.ru.md) · **简体中文**

**演练场：** 在浏览器中互转 JSON / YAML / TOML / INI ⇄ Ktav — **[ktav-lang.github.io](https://ktav-lang.github.io/)**。

[Ktav 配置格式](https://github.com/ktav-lang/spec) 的 Java 绑定。
在参考 Rust 解析器之上的一层薄封装,运行时通过
[JNA](https://github.com/java-native-access/jna) 动态加载 ——
**使用方不需要编译 JNI**,常规的 Gradle/Maven 流程即可。

需要 **JDK 17+**。JAR 由 release workflow 随每个 tag 发布到
**Maven Central**（坐标 `io.github.ktav-lang:ktav`）；原生 `ktav_cabi`
二进制则在运行时从对应的 GitHub Release 一次性获取。

## 快速开始

`build.gradle.kts`：

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.ktav-lang:ktav:0.8.1")
}
```

常规使用时，合并制品会解析其原生依赖：JNA 5.15.0（`api`）和 jackson-core 2.18.2（`implementation`）。

若在 Gradle 中仅使用纯后端，请禁用传递依赖并调用 `PureKtav`：

```kotlin
implementation("io.github.ktav-lang:ktav:0.8.1") { isTransitive = false }
```

纯后端运行时仅需 JDK 17+。其解析器、写入器、格式化器和 `ErrorEnvelope` 持有对象不使用 Jackson、JNA、JUnit、原生代码、WASM、子进程、网络或回退机制。Maven 纯后端用户需排除 `net.java.dev.jna:jna` 和 `com.fasterxml.jackson.core:jackson-core`。构建、测试和文档工具与运行时相互独立。

### 解析 —— 按类型读取字段

```java
import lang.ktav.Ktav;
import lang.ktav.Value;

String src = """
        service: web
        port: 8080
        ratio: 0.75
        tls: true
        tags: [
            prod
            eu-west-1
        ]
        db.host: primary.internal
        db.timeout: 30
        """;

Value.Obj top = (Value.Obj) Ktav.loads(src);

String  service = ((Value.Str)  top.entries().get("service")).value();
long    port    = ((Value.Int)  top.entries().get("port")).toLong();
double  ratio   = ((Value.Flt)  top.entries().get("ratio")).toDouble();
boolean tls     = ((Value.Bool) top.entries().get("tls")).value();

Value.Obj db    = (Value.Obj) top.entries().get("db");
String dbHost   = ((Value.Str) db.entries().get("host")).value();
long   dbTimeout = ((Value.Int) db.entries().get("timeout")).toLong();
```

### 遍历 —— 在 sealed `Value` 层级上分派

```java
for (var e : top.entries().entrySet()) {
    if      (e.getValue() instanceof Value.Bool b) System.out.println(e.getKey() + " is bool=" + b.value());
    else if (e.getValue() instanceof Value.Int  i) System.out.println(e.getKey() + " is int=" + i.text());
    else if (e.getValue() instanceof Value.Arr  a) System.out.println(e.getKey() + " is array(" + a.items().size() + ")");
    // ...Null / Flt / Str / Obj
}
```

JDK 21+ 上可用对 sealed type 的 `switch` 表达式。

### 构建并渲染 —— 用代码搭建文档

```java
import java.util.LinkedHashMap;
import java.util.List;

LinkedHashMap<String, Value> upstream = new LinkedHashMap<>();
upstream.put("host", new Value.Str("a.example"));
upstream.put("port", Value.Int.of(1080));

LinkedHashMap<String, Value> doc = new LinkedHashMap<>();
doc.put("name",      new Value.Str("frontend"));
doc.put("port",      Value.Int.of(8443));
doc.put("tls",       Value.Bool.TRUE);
doc.put("ratio",     Value.Flt.of(0.95));
doc.put("upstreams", new Value.Arr(List.of(new Value.Obj(upstream))));
doc.put("notes",     Value.Null.NULL);

String text = Ktav.dumps(new Value.Obj(doc));
// name: frontend
// port: 8443
// tls: true
// ratio: 0.95
// upstreams: [
//     {
//         host: a.example
//         port: 1080
//     }
// ]
// notes: null
```

完整可运行示例:[`examples/basic`](../../examples/basic/src/main/java/examples/Basic.java)。

## API

| 函数 | 作用 |
| --- | --- |
| `Ktav.loads(String) -> Value` | 将 Ktav 文档解析为 `Value` 树。 |
| `Ktav.loads(byte[]) -> Value` | 直接解析原始 UTF-8 字节；无效输入报告为 `InvalidUtf8`。 |
| `Ktav.loadsStrict(String) -> Value` | 使用严格数字词法检查解析文档。 |
| `Ktav.loadsStrict(byte[]) -> Value` | 严格解析原始 UTF-8 字节，并保留无效字节以报告 `InvalidUtf8`。 |
| `Ktav.dumps(Value) -> String` | 将 `Value` 渲染回 Ktav 文本。顶层必须是 `Obj` 或 `Arr`。 |
| `Ktav.toStringForceStrings(Value) -> String` | 输出与 `dumps` 相同，但把每个叶子标量强制为 String。 |
| `Ktav.emitCanonical(Value) -> String` | 将 `Value` 渲染为确定性的规范形式。 |
| `Ktav.format(String) -> String` | 规范化文档的写法，同时保留注释。 |
| `Ktav.canonicalFromSource(String) -> String` | 一次调用完成解析并重新输出为规范 Ktav —— 相当于 `emitCanonical(loads(src))`,但中间没有 `Value`。像 `emitCanonical` 一样丢弃注释与空行。 |
| `Ktav.nativeVersion() -> String` | 已加载的 `ktav_cabi` 的版本字符串。 |

对于 raw-byte 一致性测试，请将原始 `byte[]` 传给 `loads` 或 `loadsStrict`，让 native 解析器报告 `InvalidUtf8`；预先解码可能在 Ktav 收到数据前失败。添加 `byte[]` 重载后，未显式转换的 `Ktav.loads(null)` 和 `Ktav.loadsStrict(null)` 在编译时会有歧义。转换为 `String` 或 `byte[]` 可选择重载；两种类型的 null 都会抛出 `NullPointerException("src")`。

`toStringForceStrings` 把整数、float、布尔与 `null` 用原始标记(`::`)
压平为它们的文本形式；对象与数组保持自身结构，因为只有叶子会被强制。
结果经由 `loads` 解析回来仍是同一组 String 标量 —— 当下游消费方不理解
类型化标量时，这很有用。

### 格式化

`Ktav.format()` 接受 Ktav **源文本**并返回 Ktav 源文本 —— 它不是
`Value` 渲染器。它把结构规范化为规范形式（§ 5.9），同时保留规范
writer 会丢弃的那部分附属内容：

```java
System.out.print(Ktav.format("## the server\nserver: {host: a, port: 80}\n"));
// ## the server
// server: {
//     host: a
//     port: 80
// }
```

每条注释都逐字保留 —— Ktav 没有行尾注释（§ 3.4：注释独占一整行），
因此归属毫无歧义。空行作为分组提示保留下来，但连续两行及以上会合并为
恰好一行，紧贴括号内侧的空行填充会被丢弃，这正是该变换成为不动点的
原因：对已格式化的文本再次格式化不会有任何改变。键顺序绝不改变 ——
规范形式没有排序规则，而重排键只会让评审的 diff 更难读。

对于既没有注释**也没有空行**的文档，结果等同于
`Ktav.emitCanonical(Ktav.loads(src))`。这个更强的条件是有意的：空行
与注释一样都不属于 `Value` 模型，所以规范 writer 会丢弃它们，而
`format` 不会。

> **尚未发布（0.9 准备版本）：** 这些编辑 API 和更新后的诊断不包含在已发布的 0.8.1 包中。原生后端需要固定的 0.9 核心（C-FFI 使用 C ABI 2）；纯实现仍完全独立。

### 编辑源文本

`Ktav.editValue()` 替换 Ktav 源文本中**已存在**的值并返回新的源文本
—— 这是字节级的无损拼接（splice），而不是 parse→render 往返：

```java
String out = Ktav.editValue("port: 8080\nhost: db\n", "port",
        new Value.Int("9090"));
// port: 9090
// host: db
```

只有目标的字节会改变。注释、空行、键的拼写与顺序、BOM 以及有效编辑
范围之外的 LF / CR / CRLF 行尾风格都原样保留。dotted、inline 和块
形式按原样保留；仅当替换会损害可读性时（多行字符串、行长超过
`max(100 个 Unicode 标量值, 原物理行长)`、或需要大量转义序列的替换），
inline 包装才会展开，且只展开受影响的分支——未触及的嵌套分支保持
inline。与当前值相等的替换是字节完全一致的 no-op，即使标量拼写不
规范也一样。

路径以点分隔（`key1.key2.0.key4`）；段内 `\.` 表示字面点号，`\\`
表示字面反斜杠。`editValueSegments` 直接接收已解码的字面段——数字段
在对象中是字符串键，在数组中是从零开始的索引。不支持创建、删除或重
命名——只支持替换已存在的值。

错误是结构化的：`EditInvalidPath`（body：路径参数中发现缺陷处的
UTF-8 字节偏移）、`EditNotFound`（缺少键、索引越界、穿过标量下降、
空路径）和 `EditUnsupported`（body：原因——例如由 dotted 键与 reopen
拼成的非连续目标、会丢失容器内部注释的结构性替换，或包含独立成行的
`))` 的多行字符串）。源文本本身的问题仍以普通解析错误 envelope 报告。
纯 Java 后端提供相同操作——`PureKtav.editValue` /
`PureKtav.editValueSegments`，输出完全一致。

### 错误

解析或渲染失败时抛出 `KtavException`。除了人类可读的 `getMessage()`
之外，它还通过十个访问器携带核心错误信封其余九个结构化字段 —— `span`
被拆成 `getSpanStart()` / `getSpanEnd()`，而不是装进一个成对类型。
`getMessage()` 正是信封的第十个字段 `message`，逐字取用，而不是从其余
九个字段重新拼装出来的：

```java
try {
    Ktav.loadsStrict("version: 1.10\n");
} catch (KtavException e) {
    e.getError();        // "LossyScalar"
    e.getLine();         // 1
    e.getLineText();     // "version: 1.10"
    e.getBody();         // "1.10"  — as written
    e.getCanonical();    // "1.1"   — as it would be stored
    e.getSpecSection();  // "§3.6/§5.2"
}
```

完整集合是 `getError()`、`getReason()`、`getLine()`、`getLineText()`、
`getSpanStart()`、`getSpanEnd()`、`getPath()`、`getBody()`、
`getCanonical()`、`getSpecSection()`。缺失的信息是 `null` —— 装箱的
`Long` 返回类型正是为此而存在 —— 而不是缺少某个访问器，因此读取任何
字段都无需先判断错误类别。

`getPath()` 返回 `List<String>`，是**精确解码后的键段，绝不是拼接后的
字符串**：字面名为 `a.b` 的键是**一个**段，不可能与两段路径混淆。

writer 的两种拒绝被分开命名 —— 当 writer 能指出是哪个节点出错时用
`"UnrepresentableAt"`（此时它也会填充 `getPath()`），不能指出时用
`"Unrepresentable"`。两者的 `reason` 码相同，因此如果你只需要知道
"这次写入被拒绝了"，匹配 `getReason()` 就够了。

## 类型映射

与 Rust crate 的 `Value` 枚举完全一致 —— Ktav 每个原语一个变体,
没有有损转换:

| Ktav             | `Value` 变体                                             |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| 裸整数           | `Value.Int`(文本形式 —— 任意精度,`toBigInteger()` / `toLong()`) |
| 裸小数           | `Value.Flt`(文本形式 —— 精确往返,`toDouble()`) |
| 其他标量         | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`,保留插入顺序) |

整数与浮点数以 **文本** 保存,从而任意精度
(超过 `long` 的位数)以及十进制的精确表示都能在 parse/render
之间逐字节保持一致。

## 键的转义

自 spec 0.6.4 起,键段内的字面量 `.` 或 `:` 通过反斜杠书写:

| 写法 | 解析为 |
| --- | --- |
| `a\.b: v` | `{ "a.b": "v" }` —— 键是单个段 `a.b` |
| `a\:b: v` | `{ "a:b": "v" }` —— 键包含冒号 |
| `x.y\.z: v` | `{ "x": { "y.z": "v" } }` —— 仅在第一个点处分割；被转义的点不再分割 |

键中的字面量反斜杠写作 `\\`。

## 原生库的查找顺序

首次调用时,Java 库按如下顺序查找 `ktav_cabi`:

1. **`$KTAV_LIB_PATH`** —— 指向本地构建的绝对路径。适合开发
   和离线 CI。
2. **用户缓存** —— `<userCache>/ktav-java/v<版本>/…`,
   之前调用下载过的。
3. **从 GitHub Release 下载** —— 从
   `github.com/ktav-lang/java/releases/download/v<版本>/<名称>`
   下载一次对应平台的资产,并缓存到 (2)。安装后首次调用需要网络。

`<userCache>` 在 Windows 上是 `%LOCALAPPDATA%`,macOS 上是
`~/Library/Caches`,Linux 上是 `$XDG_CACHE_HOME` 或 `~/.cache`。

## 纯 Java 后端

从 0.8.1 起，库在 `lang.ktav.pure` 包中随附第二个纯 Java 后端，
由 `PureKtav` 门面提供：

| 函数 | 作用 |
| --- | --- |
| `PureKtav.loads(byte[])` / `PureKtav.loads(String)` | 将 Ktav 文档解析为 `Value` 树。`byte[]` 入口接受原始字节，并在运行语法之前校验 UTF-8（§ 6.15）；错误区间和行号按原始字节（ORIGINAL raw bytes）报告，跳过前导 BOM 后不会重新偏移。 |
| `PureKtav.loadsStrict(byte[])` / `PureKtav.loadsStrict(String)` | 使用严格数字词法检查解析文档。 |
| `PureKtav.dumps(Value)` | 将 `Value` 渲染回 Ktav 文本。顶层必须是 `Obj` 或 `Arr`。 |
| `PureKtav.toStringForceStrings(Value)` | 输出与 `dumps` 相同，但把每个叶子标量强制为 String。 |
| `PureKtav.emitCanonical(Value)` | 将 `Value` 渲染为确定性的规范形式。规范浮点数遵循 § 5.9.8：最短可回读十进制表示，仅当 `abs >= 1e7` 或 `abs < 1e-2` 时使用科学计数法。 |
| `PureKtav.canonicalFromSource(String)` | 一次调用完成解析并重新输出为规范 Ktav。 |
| `PureKtav.format(String)` | 规范化文档写法，保留注释。输出的换行符始终只有 LF（输入接受 LF、CR 和 CRLF）。 |
| `PureKtav.version()` | 纯后端版本 —— 其实现的规范版本。 |

### 纯 Java 后端的独立安装

合并制品的构建将 JNA 5.15.0 声明为 `api`，将 jackson-core 2.18.2 声明为 `implementation`，用于原生 ABI 和 JSON wire。标准 Maven/Gradle 依赖解析会包含这些依赖，因此常规安装并非零依赖安装。这里描述的是构建声明，并非已核实的发布元数据。

Gradle 纯后端用法可关闭传递依赖：

```kotlin
implementation("io.github.ktav-lang:ktav:0.8.1") { isTransitive = false }
```

Maven 请同时排除 `net.java.dev.jna:jna` 和 `com.fasterxml.jackson.core:jackson-core`；也可直接使用本库 JAR。纯后端运行需要 JDK 17，不需要第三方运行时库。纯后端的成功和错误路径均不使用 JNA、Jackson、JUnit、原生代码、WASM、子进程、网络或回退。原生后端用法需要其依赖；请为其保留常规依赖解析。
该门面逐操作镜像 `Ktav`，其设计目标是产出相同的 `Value` 树和相同的
`KtavException` 信封，因此针对一个后端编写的代码，换到另一个后端
无需改动。没有全局后端开关：后端通过显式调用选择 —— `Ktav`
（原生）或 `PureKtav`（纯）。

纯模式从不加载或下载原生库 —— 没有 JNA、没有 `ktav_cabi`、没有缓存
目录、没有网络。它适合离线（air-gapped）环境、没有预编译原生资产的
平台，以及任何在乎纯 JVM 依赖的场景。原生后端仍是按参考实现调优的
路径。

纯后端的限制：嵌套深度（多行复合框架、inline 递归、dotted 路径
长度）以 256 为界（`PureParser.MAX_DEPTH`）；标量位于 i64 / binary64
域，与原生核心完全一致。该限制只作用于解析——写入器是迭代的，
输出任意深度的树都没有深度限制。与原生后端逐字节一致的规范输出是由 `CrossBackendTest` 在 CI 中检查的契约；其余写入器遵循参考写入器的文档化布局
（4 空格缩进、插入顺序、§ 5.9.10 键形式选择）。

## 运行时支持

- JDK 17+(sealed 接口)。
- 预编译二进制覆盖:`linux/amd64`、`linux/arm64`、`darwin/amd64`、
  `darwin/arm64`、`windows/amd64`、`windows/arm64`。
- Linux 发行版需使用 glibc 2.17+(Rust 默认目标)。Alpine
  (musl)支持已规划。

## 许可证

MIT OR Apache-2.0 —— 见 [LICENSE-MIT](../../LICENSE-MIT) 和 [LICENSE-APACHE](../../LICENSE-APACHE)。

## 其他 Ktav 实现

- [`spec`](https://github.com/ktav-lang/spec) —— 规范 + 一致性测试套件
- [`rust`](https://github.com/ktav-lang/rust) —— 参考 Rust crate(`cargo add ktav`)
- [`csharp`](https://github.com/ktav-lang/csharp) —— C# / .NET(`dotnet add package Ktav`)
- [`golang`](https://github.com/ktav-lang/golang) —— Go(`go get github.com/ktav-lang/golang`)
- [`js`](https://github.com/ktav-lang/js) —— JS / TS(`npm install @ktav-lang/ktav`)
- [`php`](https://github.com/ktav-lang/php) —— PHP(`composer require ktav-lang/ktav`)
- [`python`](https://github.com/ktav-lang/python) —— Python(`pip install ktav`)
