>>>>> lang=en
## 0.1.1 — Maven Central + review fixes

First publication to Maven Central as
`io.github.ktav-lang:ktav:0.1.1`. Pull via:

```kotlin
implementation("io.github.ktav-lang:ktav:0.1.1")
```

### Fixed

- `Ktav.callNative` wraps the JNA `Memory` in try-with-resources so
  the native buffer is released even if the FFI call throws (was
  leaking until the next GC finalizer pass).
- `Value.Obj` / `Value.Arr` defensive-copy in their compact
  constructor and expose unmodifiable views — the records were
  advertised as immutable but callers could mutate the entries map
  / items list through the accessor.
- `NativeLoader.download` now `fsync`s the body bytes through a
  `WRITE`-opened `FileChannel` before the rename, eliminating a
  cache-corruption window after crash.
- `NativeLoader.testOverride` is `volatile` for safe publication
  under JUnit parallel execution.
- `ConformanceTest` closes the `Files.walk` stream via
  try-with-resources before returning to `@TestFactory`, fixing a
  directory-iterator handle leak (manifested as `FileSystemException`
  on Windows).
- `SmokeTest.roundTripSimpleDocument` now asserts every key it
  inserts (`ratio`, `nested.inner` were silently unverified).

>>>>> lang=ru
## 0.1.1 — Maven Central + review fixes

Первая публикация в Maven Central как
`io.github.ktav-lang:ktav:0.1.1`. Подключение:

```kotlin
implementation("io.github.ktav-lang:ktav:0.1.1")
```

### Исправлено

- `Ktav.callNative` оборачивает JNA `Memory` в try-with-resources —
  native-буфер освобождается, даже если FFI-вызов бросит исключение
  (раньше утечка до следующего GC finalizer).
- `Value.Obj` / `Value.Arr` делают defensive copy в compact-
  конструкторе и отдают неизменяемые view'ы — записи претендовали на
  immutable, но через аксессор клиент мог мутировать содержимое.
- `NativeLoader.download` `fsync`-ает байты тела через `WRITE`-
  открытый `FileChannel` перед rename — закрыто окно повреждения
  кеша после crash.
- `NativeLoader.testOverride` — `volatile` для корректной видимости
  при JUnit-параллелизации.
- `ConformanceTest` закрывает `Files.walk`-стрим через try-with-
  resources до возврата `@TestFactory` — фикс утечки directory-
  iterator handle (на Windows проявлялось как `FileSystemException`).
- `SmokeTest.roundTripSimpleDocument` теперь проверяет каждый ключ,
  который кладёт во вход (`ratio`, `nested.inner` молча не
  ассертились).

>>>>> lang=zh
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

