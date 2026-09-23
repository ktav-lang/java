>>>>> lang=en
## Scope

Issues that count as security problems for this package:

- Out-of-bounds reads / writes or panics in the native `ktav_cabi`
  shared library that crash or hang the host JVM. The library is
  loaded via JNA (`Native.load`), so a native crash tears down the
  whole JVM — no Java-side `catch` can stop it.
- Runaway memory or CPU when parsing crafted input.
- Incorrect FFI memory handling (double-free, missing free, reading
  freed buffers across the `ktav_free` boundary).
- Any behaviour that allows crafted Ktav input to escape the expected
  value-domain (arbitrary code execution in the loaded library,
  uninitialised-memory disclosure, etc.).
- A download-time vector: `NativeLoader` fetches the prebuilt
  binary from the matching GitHub Release. Reports about TLS /
  integrity-check gaps in that path belong here.

Issues that are **not** security problems here — please use regular
issues for these:

- Performance regressions without crash / hang characteristics.
- Behavioural mismatches that aren't exploitable.
- Problems in the Ktav format itself — those belong in
  [`ktav-lang/spec`](https://github.com/ktav-lang/spec).
>>>>> lang=ru
## Область

Что считается проблемой безопасности для этого пакета:

- Out-of-bounds чтения / записи или паники в нативной библиотеке
  `ktav_cabi`, ведущие к падению или зависанию хост-JVM. Библиотека
  грузится через JNA (`Native.load`) — нативный crash роняет всю JVM,
  на Java-стороне его никаким `catch` не поймать.
- Неконтролируемое потребление памяти или CPU при разборе специально
  сформированного входа.
- Некорректное обращение с памятью на FFI-границе (double-free,
  missing free, чтение освобождённого буфера после `ktav_free`).
- Любое поведение, при котором сформированный Ktav-вход выходит за
  ожидаемый value-домен (произвольное выполнение кода в загруженной
  библиотеке, раскрытие неинициализированной памяти и т. п.).
- Download-time вектор: `NativeLoader` качает прекомпилированный
  бинарь из соответствующего GitHub Release. Репорты про TLS /
  проверку целостности этой цепочки сюда.

Что **не** считается проблемой безопасности здесь — пожалуйста,
используйте обычные issue:

- Регрессии производительности без характеристик crash / hang.
- Поведенческие расхождения, которые не эксплуатируются.
- Проблемы в самом формате Ktav — им место в
  [`ktav-lang/spec`](https://github.com/ktav-lang/spec).
>>>>> lang=zh
## 范围

以下问题会按本包的安全问题处理：

- 原生 `ktav_cabi` 库中的越界读写或 panic，导致宿主 JVM 崩溃或
  挂起。库通过 JNA（`Native.load`）加载 —— 原生崩溃会直接拉垮
  整个 JVM，Java 侧任何 `catch` 都拦不住。
- 解析构造输入时出现失控的内存或 CPU 消耗。
- FFI 边界上的内存处理错误（double-free、missing free、`ktav_free`
  后读取已释放缓冲区）。
- 任何允许构造的 Ktav 输入逃逸出预期值域的行为（加载库内的任意代码
  执行、未初始化内存泄露等）。
- 下载期向量：`NativeLoader` 会从对应的 GitHub Release 拉取
  预编译二进制。该路径上的 TLS / 完整性校验缺陷也上报到这里。

以下**不**算本包的安全问题 —— 请走普通 issue：

- 没有崩溃 / 挂起特征的性能回归。
- 不可利用的行为差异。
- Ktav 格式本身的问题 —— 这类问题属于
  [`ktav-lang/spec`](https://github.com/ktav-lang/spec)。
