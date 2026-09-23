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

