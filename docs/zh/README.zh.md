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

需要 **JDK 17+**。目前通过 GitHub Releases 分发
(已规划发布到 Maven Central)。

## 快速开始

`build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    // while we're not yet on Maven Central, consume the JAR from
    // the GitHub Release — see the README for a worked example.
}

dependencies {
    implementation("io.github.ktav-lang:ktav:0.8.0")
    implementation("net.java.dev.jna:jna:5.15.0")
}
```

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
| `Ktav.loadsStrict(String) -> Value` | 使用严格数字词法检查解析文档。 |
| `Ktav.dumps(Value) -> String` | 将 `Value` 渲染回 Ktav 文本。顶层必须是 `Obj`。 |
| `Ktav.toStringForceStrings(Value) -> String` | 输出与 `dumps` 相同，但把每个叶子标量强制为 String。 |
| `Ktav.emitCanonical(Value) -> String` | 将 `Value` 渲染为确定性的规范形式。 |
| `Ktav.format(String) -> String` | 规范化文档的写法，同时保留注释。 |
| `Ktav.canonicalFromSource(String) -> String` | 一次调用完成解析并重新输出为规范 Ktav —— 相当于 `emitCanonical(loads(src))`,但中间没有 `Value`。像 `emitCanonical` 一样丢弃注释与空行。 |
| `Ktav.nativeVersion() -> String` | 已加载的 `ktav_cabi` 的版本字符串。 |

`toStringForceStrings` 把整数、float、布尔与 `null` 用原始标记(`::`)
压平为它们的文本形式；对象与数组保持自身结构，因为只有叶子会被强制。
结果经由 `loads` 解析回来仍是同一组 String 标量 —— 当下游消费方不理解
类型标记时，这很有用。

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

```text
a\.b: v        // key is the single segment "a.b" -> { "a.b": "v" }
a\:b: v        // key contains a colon            -> { "a:b": "v" }
x.y\.z: v      // split on the first dot only     -> { "x": { "y.z": "v" } }
```

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
