>>>>> lang=en
## The pure-Java backend

Since 0.8.1 the library ships a second, pure-Java backend in the
`lang.ktav.pure` package, behind the `PureKtav` facade:

| Function | Purpose |
| --- | --- |
| `PureKtav.loads(byte[])` / `PureKtav.loads(String)` | Parse a Ktav document into the `Value` tree. The `byte[]` entries take raw bytes and validate UTF-8 (§ 6.15) before the grammar runs; error spans and line numbers are reported over the ORIGINAL raw bytes (they are not rebased after a leading BOM is skipped). |
| `PureKtav.loadsStrict(byte[])` / `PureKtav.loadsStrict(String)` | Parse with strict numeric spelling checks. |
| `PureKtav.dumps(Value)` | Render a `Value` back as Ktav text. Top-level must be an `Obj` or `Arr`. |
| `PureKtav.toStringForceStrings(Value)` | Render like `dumps`, but coerce every leaf scalar to a String. |
| `PureKtav.emitCanonical(Value)` | Render a `Value` as deterministic canonical form. Canonical floats follow § 5.9.8: the shortest round-trip decimal, scientific notation iff `abs >= 1e7` or `abs < 1e-2`. |
| `PureKtav.canonicalFromSource(String)` | Parse and re-emit as canonical Ktav in one call. |
| `PureKtav.format(String)` | Normalise a document's spelling, keeping comments. Output line endings are always LF-only (LF, CR, and CRLF inputs are all accepted). |
| `PureKtav.version()` | Version of the pure backend — the spec version it implements. |

### Isolated pure-Java installation

The combined artifact's build declares JNA 5.15.0 as `api` and jackson-core 2.18.2 as `implementation` for native ABI and JSON-wire support. Standard Maven/Gradle dependency resolution includes those dependencies, so the normal install is not zero-dependency. This describes the build declarations, not verified published metadata.

For pure-only Gradle use, disable transitive dependencies:

```kotlin
implementation("io.github.ktav-lang:ktav:0.8.1") { isTransitive = false }
```

For Maven, exclude both `net.java.dev.jna:jna` and `com.fasterxml.jackson.core:jackson-core`; alternatively, use the library JAR directly. Pure-only runtime use requires JDK 17 and no third-party runtime libraries. Pure success and error paths use no JNA, Jackson, JUnit, native code, WASM, subprocesses, network access, or fallback. Native backend use requires its dependencies; retain normal dependency resolution for it.

>>>>> lang=ru
## Чисто-Java бэкенд

Начиная с 0.8.1, библиотека содержит второй, чисто-Java бэкенд в
пакете `lang.ktav.pure` за фасадом `PureKtav`:

| Функция | Назначение |
| --- | --- |
| `PureKtav.loads(byte[])` / `PureKtav.loads(String)` | Разобрать Ktav-документ в дерево `Value`. Вариант с `byte[]` принимает сырые байты и валидирует UTF-8 (§ 6.15) до запуска грамматики; спаны ошибок и номера строк сообщаются по ИСХОДНЫМ сырым байтам (не сдвигаются после пропуска ведущего BOM). |
| `PureKtav.loadsStrict(byte[])` / `PureKtav.loadsStrict(String)` | Разобрать документ со строгой проверкой записи чисел. |
| `PureKtav.dumps(Value)` | Отрендерить `Value` обратно в Ktav-текст. Верхний уровень должен быть `Obj` или `Arr`. |
| `PureKtav.toStringForceStrings(Value)` | Отрендерить как `dumps`, но привести каждый leaf-скаляр к String. |
| `PureKtav.emitCanonical(Value)` | Отрендерить `Value` в детерминированной канонической форме. Канонические числа с плавающей точкой следуют § 5.9.8: кратчайшая десятичная запись с обратимым округлением, научная запись только при `abs >= 1e7` или `abs < 1e-2`. |
| `PureKtav.canonicalFromSource(String)` | Разобрать и заново вывести как канонический Ktav за один вызов. |
| `PureKtav.format(String)` | Нормализовать написание документа, сохраняя комментарии. Переводы строк в выводе — всегда только LF (на входе принимаются LF, CR и CRLF). |
| `PureKtav.version()` | Версия чистого бэкенда — реализованная им версия спецификации. |

### Изолированная установка чистого Java-бэкенда

Сборка объединённого артефакта объявляет JNA 5.15.0 как `api` и jackson-core 2.18.2 как `implementation` для нативного ABI и JSON wire. Стандартное разрешение зависимостей Maven/Gradle включает эти зависимости, поэтому обычная установка не является установкой без зависимостей. Это описание объявлений сборки, а не проверенных опубликованных метаданных.

Для использования только чистого бэкенда в Gradle отключите транзитивные зависимости:

```kotlin
implementation("io.github.ktav-lang:ktav:0.8.1") { isTransitive = false }
```

В Maven исключите `net.java.dev.jna:jna` и `com.fasterxml.jackson.core:jackson-core`; альтернативно используйте JAR библиотеки напрямую. Для выполнения только чистого бэкенда нужен JDK 17, сторонние runtime-библиотеки не нужны. Успешные и ошибочные пути чистого бэкенда не используют JNA, Jackson, JUnit, нативный код, WASM, процессы, сеть или fallback. Нативному бэкенду нужны его зависимости; для него сохраняйте обычное разрешение зависимостей.

>>>>> lang=zh
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
该门面镜像 `Ktav` 的共享操作，并旨在生成相同的 `Value` 树和
`KtavException` 信封；对于这些操作，代码可在两个后端之间无修改使用。
源文本编辑包括替换、插入、删除和字面键重命名，支持文本路径和解码段。
成功编辑保留未触及的源文本并验证预期树；不支持的源形式返回
`EditUnsupported`。插入和重命名冲突返回 `EditAlreadyExists`。
具体编辑契约见上文。没有全局后端
开关：通过显式调用 `Ktav`（原生）或 `PureKtav`（纯）选择后端。

