# Changelog

**语言:** [English](CHANGELOG.md) · [Русский](CHANGELOG.ru.md) · **简体中文**

Java 绑定的所有显著变更记录于此。格式基于
[Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/);版本号遵循
[Semantic Versioning](https://semver.org/),采用 pre-1.0 约定:
MINOR 递增视为破坏性变更。

本 changelog 跟踪 **绑定发布**,不覆盖 Ktav 格式自身的变更 ——
后者见 [`ktav-lang/spec`](https://github.com/ktav-lang/spec/blob/main/CHANGELOG.md)。

## 0.1.2 —— 2026-04-26

### 变更

- **升级到 `ktav 0.1.4`** —— 上游 Rust crate 中 `cabi` 使用的 untyped
  `parse() → Value` 路径,小文档加速约 30%、大文档加速约 13%,只是
  `Frame::Object` 的初始容量微调(4 → 8)。每次 `Ktav.loads` 都会
  透明地受益。

Maven 坐标:`io.github.ktav-lang:ktav:0.1.2`。依赖声明:

```kotlin
implementation("io.github.ktav-lang:ktav:0.1.2")
```

## 0.1.1 —— Maven Central + 评审修复

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

## 0.1.0 —— 首次公开发布

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

| Ktav             | `Value` 变体                                             |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| `:i <digits>`    | `Value.Int`(文本形式 —— 任意精度)                      |
| `:f <number>`    | `Value.Flt`(文本形式 —— 精确往返)                      |
| 裸 scalar        | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`)            |

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
