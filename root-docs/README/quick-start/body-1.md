>>>>> lang=en
## Quick start

`build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.ktav-lang:ktav:0.8.1")
}
```

For normal use, the combined artifact resolves its native dependencies: JNA 5.15.0 (`api`) and jackson-core 2.18.2 (`implementation`).

For pure-only Gradle use, disable transitive dependencies and call `PureKtav`:

```kotlin
implementation("io.github.ktav-lang:ktav:0.8.1") { isTransitive = false }
```

Pure runtime requires only JDK 17+. Its parser, writer, formatter, and `ErrorEnvelope` holder use no Jackson, JNA, JUnit, native code, WASM, subprocess, network, or fallback. Maven pure-only consumers must exclude both `net.java.dev.jna:jna` and `com.fasterxml.jackson.core:jackson-core`. Build, test, and documentation tooling is separate from runtime.

>>>>> lang=ru
## Быстрый старт

`build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.ktav-lang:ktav:0.8.1")
}
```

При обычном использовании объединённый артефакт разрешает нативные зависимости: JNA 5.15.0 (`api`) и jackson-core 2.18.2 (`implementation`).

Для использования только чистого backend в Gradle отключите транзитивные зависимости и вызывайте `PureKtav`:

```kotlin
implementation("io.github.ktav-lang:ktav:0.8.1") { isTransitive = false }
```

Для runtime чистого backend требуется только JDK 17+. Его парсер, writer, formatter и контейнер `ErrorEnvelope` не используют Jackson, JNA, JUnit, native-код, WASM, подпроцессы, сеть или fallback. Для чистого backend в Maven исключите `net.java.dev.jna:jna` и `com.fasterxml.jackson.core:jackson-core`. Инструменты сборки, тестирования и документации отделены от runtime.

>>>>> lang=zh
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

