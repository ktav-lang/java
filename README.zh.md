# ktav — Java 绑定

**Languages:** [English](README.md) · [Русский](README.ru.md) · **简体中文**

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
    // 尚未发布到 Maven Central 之前,请直接从 GitHub Release
    // 拉取 JAR —— README 中有完整示例。
}

dependencies {
    implementation("io.github.ktav-lang:ktav:0.1.0")
    implementation("net.java.dev.jna:jna:5.15.0")
}
```

### 解析 —— 按类型读取字段

```java
import lang.ktav.Ktav;
import lang.ktav.Value;

String src = """
        service: web
        port:i 8080
        ratio:f 0.75
        tls: true
        tags: [
            prod
            eu-west-1
        ]
        db.host: primary.internal
        db.timeout:i 30
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
// port:i 8443
// tls: true
// ratio:f 0.95
// upstreams: [
//     {
//         host: a.example
//         port:i 1080
//     }
// ]
// notes: null
```

完整可运行示例:[`examples/basic`](examples/basic/src/main/java/examples/Basic.java)。

## API

| 函数 | 作用 |
| --- | --- |
| `Ktav.loads(String) -> Value` | 将 Ktav 文档解析为 `Value` 树。 |
| `Ktav.dumps(Value) -> String` | 将 `Value` 渲染回 Ktav 文本。顶层必须是 `Obj`。 |
| `Ktav.nativeVersion() -> String` | 已加载的 `ktav_cabi` 的版本字符串。 |

解析 / 渲染出错时抛出 `KtavException` —— 消息内容是由原生
解析器返回的 UTF-8 字符串。

## 类型映射

与 Rust crate 的 `Value` 枚举完全一致 —— Ktav 每个原语一个变体,
没有有损转换:

| Ktav             | `Value` 变体                                             |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| `:i <digits>`    | `Value.Int`(文本形式 —— 任意精度,`toBigInteger()` / `toLong()`) |
| `:f <number>`    | `Value.Flt`(文本形式 —— 精确往返,`toDouble()`) |
| bare scalar      | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`,保留插入顺序) |

带类型的整数与浮点数以 **文本** 保存,从而任意精度
(超过 `long` 的位数)以及十进制的精确表示都能在 parse/render
之间逐字节保持一致。

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

MIT —— 见 [LICENSE](LICENSE)。

## 其他 Ktav 实现

- [`spec`](https://github.com/ktav-lang/spec) —— 规范 + 一致性测试套件
- [`rust`](https://github.com/ktav-lang/rust) —— 参考 Rust crate(`cargo add ktav`)
- [`csharp`](https://github.com/ktav-lang/csharp) —— C# / .NET(`dotnet add package Ktav`)
- [`golang`](https://github.com/ktav-lang/golang) —— Go(`go get github.com/ktav-lang/golang`)
- [`js`](https://github.com/ktav-lang/js) —— JS / TS(`npm install @ktav-lang/ktav`)
- [`php`](https://github.com/ktav-lang/php) —— PHP(`composer require ktav-lang/ktav`)
- [`python`](https://github.com/ktav-lang/python) —— Python(`pip install ktav`)
