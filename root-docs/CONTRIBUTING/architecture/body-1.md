>>>>> lang=en
## Architecture notes

- **Wire format.** Rust and Java exchange JSON over the FFI boundary,
  with `{"$i":"..."}` / `{"$f":"..."}` wrappers for typed integers /
  floats. This preserves arbitrary precision and the Integer vs Float
  distinction through encoding / decoding.
- **Memory ownership.** Rust allocates the output buffer; Java copies
  it into a `byte[]` and immediately calls `ktav_free` on the Rust
  side. No buffer is long-lived across the FFI boundary.
- **Loader.** `lang.ktav.internal.NativeLib` dlopens the shared library
  once per process via JNA's `Native.load`. The path is resolved by
  `NativeLoader.resolve()` — env / cache / download.
- **Pure backend.** Since 0.8.1, `lang.ktav.pure` (facade `PureKtav`)
  mirrors the native backend in plain Java — same `Value` tree, same
  `KtavException` envelopes, same operation set — and never loads or
  downloads the native library. Its parser, writer, formatter, and shared
  `ErrorEnvelope` holder (`of` / getters) are JDK-only; `ErrorEnvelope.parse`
  delegates to the separate native Jackson error codec. Other native JSON
  wire operations also use Jackson. The combined artifact declares JNA
  5.15.0 as `api` for native FFI and jackson-core 2.18.2 as
  `implementation` for native JSON codecs. Normal dependency resolution
  includes these dependencies; pure-only consumers can avoid both with
  Gradle `isTransitive = false` or Maven exclusions. JDK 17+ suffices to
  run the pure backend with the artifact JAR available. Build, test, and
  documentation tooling is separate from runtime dependencies. Dual-backend
  tests run the conformance corpus through both; `CrossBackendTest` checks
  roundtrips between them; the `test-pure` CI job runs the pure suite with
  no cargo step.

>>>>> lang=ru
## Архитектурные заметки

- **Wire-формат.** Rust и Java обмениваются JSON через FFI-границу,
  с обёртками `{"$i":"..."}` / `{"$f":"..."}` для типизированных
  integer / float. Так сохраняются произвольная точность и различие
  Integer vs Float при кодировании / декодировании.
- **Владение памятью.** Rust аллоцирует выходной буфер; Java копирует
  его в `byte[]` и сразу вызывает `ktav_free` на Rust-стороне. Ни один
  буфер не живёт долго через FFI-границу.
- **Loader.** `lang.ktav.internal.NativeLib` dlopen'ит библиотеку
  один раз на процесс через `Native.load` из JNA. Путь резолвит
  `NativeLoader.resolve()` — env / кэш / скачивание.
- **Чистый бэкенд.** С 0.8.1 `lang.ktav.pure` (фасад `PureKtav`)
  зеркалит нативный бэкенд на чистом Java — то же дерево `Value`, те же
  конверты `KtavException`, тот же набор операций — и никогда не
  загружает и не скачивает нативную библиотеку. Его парсер, writer,
  formatter и общий контейнер `ErrorEnvelope` (`of` / getters) работают
  только на JDK; `ErrorEnvelope.parse` делегирует отдельному нативному
  Jackson error codec. Другие нативные операции JSON wire также используют
  Jackson. Объединённый артефакт объявляет JNA 5.15.0 как `api` для native
  FFI и jackson-core 2.18.2 как `implementation` для нативных JSON codec.
  Обычное разрешение включает эти зависимости; чистый backend позволяет
  исключить обе через Gradle `isTransitive = false` или Maven exclusions.
  Для запуска чистого backend с JAR артефакта достаточно JDK 17+.
  Инструменты сборки, тестирования и документации отделены от runtime.
  Двухбэкендные тесты прогоняют conformance-корпус через оба;
  `CrossBackendTest` проверяет roundtrip между ними; CI-джоба `test-pure`
  запускает чистый набор без cargo-шага.

>>>>> lang=zh
## 架构笔记

- **Wire 格式。** Rust 与 Java 通过 FFI 边界交换 JSON，并用
  `{"$i":"..."}` / `{"$f":"..."}` 包装带类型的整数 / 浮点。
  这样可在编码 / 解码中保留任意精度以及 Integer 与 Float 的区分。
- **内存所有权。** Rust 分配输出缓冲；Java 将其复制到 `byte[]`，
  并立即在 Rust 侧调用 `ktav_free`。没有缓冲能长期跨越 FFI 边界。
- **加载器。** `lang.ktav.internal.NativeLib` 通过 JNA 的
  `Native.load` 每进程 dlopen 一次库。路径由
  `NativeLoader.resolve()` 决定 —— env / 缓存 / 下载。
- **纯后端。** 从 0.8.1 起，`lang.ktav.pure`（门面 `PureKtav`）用纯
  Java 镜像原生后端 —— 相同的 `Value` 树、相同的 `KtavException`
  信封、相同的操作集合 —— 且从不加载或下载原生库。其解析器、写入器、
  格式化器和共享 `ErrorEnvelope` 持有对象（`of` / getters）仅依赖 JDK；
  `ErrorEnvelope.parse` 委托给独立的原生 Jackson 错误 codec。其他原生
  JSON wire 操作也使用 Jackson。合并制品将 JNA 5.15.0 声明为原生 FFI
  的 `api`，将 jackson-core 2.18.2 声明为原生 JSON codec 的
  `implementation`。常规依赖解析会包含这些依赖；纯后端用户可通过
  Gradle `isTransitive = false` 或 Maven exclusions 排除两者。使用制品
  JAR 运行纯后端只需 JDK 17+。构建、测试和文档工具与运行时依赖相互
  独立。双后端测试会对两者运行一致性语料；`CrossBackendTest` 检查
  两者间的往返；CI 的 `test-pure` 作业不执行 cargo 步骤而运行纯后端套件。

