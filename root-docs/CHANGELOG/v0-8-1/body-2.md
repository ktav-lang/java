>>>>> lang=en
- **Rust core 0.8.1 upgrade.** The Rust workspace builds against the
  published `ktav = "0.8.1"` crate from crates.io (no path overrides),
  and the Java artifact version moves to **0.8.1** in step. The spec
  submodule pin moves to the spec 0.8.1 errata.

### Fixed

- **Spec 0.8.1 errata implemented in the pure backend.** An explicitly
  opened root compound that is never closed is diagnosed as
  `UnclosedCompound`; UTF-8 validation (§ 6.15) runs before the grammar
  and reports line numbers and a nonempty byte span over the raw
  bytes, so invalid input is never misdiagnosed as a grammar error;
  base prefixes on integer literals are lowercase-only (§ 5.2) —
  uppercase `0X`/`0O`/`0B` spellings such as `0X1A` are Strings, and
  the errata confirms this reading.
- Test suite and CI: a dual-backend corpus harness runs the spec
  conformance corpus through both backends; `CrossBackendTest` asserts
  roundtrips across them; a `test-pure` CI job runs the full pure
  suite with **no cargo build step at all**, proving the pure backend
  never touches the native library.

>>>>> lang=ru
- **Обновление Rust-ядра до 0.8.1.** Rust-workspace собирается против
  опубликованного crate `ktav = "0.8.1"` с crates.io (без
  path-оверрайдов), а версия Java-артефакта синхронно поднимается до
  **0.8.1**. Пин сабмодуля spec переезжает на errata спецификации
  0.8.1.

### Исправлено

- **Errata спецификации 0.8.1 реализована в чистом бэкенде.** Явно
  открытый корневой компаунд без закрытия диагностируется как
  `UnclosedCompound`; валидация UTF-8 (§ 6.15) выполняется до
  грамматики и сообщает номера строк и непустой байтовый спан по сырым
  байтам, поэтому некорректный ввод больше не диагностируется как
  грамматическая ошибка; префиксы оснований у целочисленных литералов
  — только строчные (§ 5.2): записи в верхнем регистре `0X`/`0O`/`0B`
  (например, `0X1A`) — это String, и errata подтверждает такое
  прочтение.
- Тесты и CI: корпусный харнес с двумя бэкендами прогоняет
  conformance-корпус спецификации через оба бэкенда; `CrossBackendTest`
  проверяет roundtrip между ними; CI-джоба `test-pure` запускает
  полный чистый набор **вообще без сборки cargo**, что доказывает:
  чистый бэкенд никогда не трогает нативную библиотеку.

>>>>> lang=zh
- **Rust 核心升级至 0.8.1。** Rust workspace 改为针对 crates.io 上
  已发布的 `ktav = "0.8.1"` crate 构建（无 path 覆盖），Java 构件
  版本同步升至 **0.8.1**。spec 子模块固定版本迁至规范 0.8.1 errata。

### 修复

- **纯后端实现了规范 0.8.1 errata。** 显式打开且从未关闭的根复合
  结构会诊断为 `UnclosedCompound`；UTF-8 校验（§ 6.15）在语法分析
  之前运行，并按原始字节报告行号和非空字节区间，因此无效输入不会再
  被误诊为语法错误；整数字面量的进制前缀仅允许小写（§ 5.2）
  —— 大写的 `0X`/`0O`/`0B` 写法（如 `0X1A`）是 String，errata
  确认了这一读法。
- 测试与 CI：双后端语料 harness 将规范 conformance 语料跑过两个
  后端；`CrossBackendTest` 断言它们之间的 roundtrip；CI 的
  `test-pure` 作业在**完全不进行 cargo 构建**的情况下运行完整纯
  后端套件，证明纯后端从不触碰原生库。

