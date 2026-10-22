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
  downloads the native library. Dual-backend tests run the conformance
  corpus through both; `CrossBackendTest` checks roundtrips between
  them; the `test-pure` CI job runs the pure suite with no cargo step.

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
  загружает и не скачивает нативную библиотеку. Двухбэкендные тесты
  прогоняют conformance-корпус через оба; `CrossBackendTest` проверяет
  roundtrip между ними; CI-джоба `test-pure` запускает чистый набор
  без cargo-шага.

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
  信封、相同的操作集合 —— 且从不加载或下载原生库。双后端测试把
  conformance 语料跑过两者；`CrossBackendTest` 检查它们之间的
  roundtrip；CI 的 `test-pure` 作业在不进行 cargo 构建的情况下运行
  纯后端套件。

