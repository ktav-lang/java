>>>>> lang=en
- Migrated `crates/cabi` to a single `ktav::declare_cabi!()` invocation
  (ktav's `cabi` feature) instead of a hand-rolled C ABI shim; the
  exported symbol surface is unchanged, so the Java API is unaffected.
  The 0.8.0 dependency floor and `v0.8.0` spec pin add § 5.2: a decimal
  with a redundant leading zero parses
  as a String, not an Integer.
- The artifact version moves to **0.8.0**, in step with the core and the
  specification; the prebuilt-library download fallback now targets the
  `v0.8.0` release asset.
- The conformance runner reads `spec/versions/0.8/tests` (it silently
  kept reading the stale `0.7` corpus after the submodule was re-pinned
  to `0.8.0` — the path was hardcoded, not derived from the pin) and
  executes every fixture category the corpus ships, including the new
  `strict-lossy/` (`loads()` must equal the lax value, `loadsStrict()`
  must throw with the matching reason, body and canonical form). A
  guard test fails the build if an unrecognized category directory
  appears under the corpus, so a future addition can't repeat this
  silently.
- Carried over from the unreleased 0.7 cycle (history — superseded by
  the 0.8.0 pin in the same window): `rust-version` raised to `1.71`
  (the ktav 0.7 MSRV); the runner began executing the `unrepresentable/`
  and `parseable-unrepresentable/` categories; `emitCanonical` output
  is compared byte-for-byte against every valid fixture's
  `.canonical.ktav` companion (spec § 5.9.10, § 5.9.8); the
  corpus-population guard came to cover every fixture category.

>>>>> lang=ru
- `crates/cabi` переведён на один вызов `ktav::declare_cabi!()` (фича
  `cabi` крейта ktav) вместо рукописной C ABI-прослойки; набор
  экспортируемых символов не изменился, поэтому Java API не затронут.
  Нижняя граница зависимости **0.8.0** и закрепление spec на `v0.8.0`
  добавляют § 5.2: десятичное число с избыточным ведущим нулём
  разбирается как String, а не Integer.
- Версия пакета — **0.8.0**, синхронно с ядром и спецификацией; резервная
  загрузка готовой библиотеки теперь берёт ассет релиза `v0.8.0`.
- Conformance-раннер читает `spec/versions/0.8/tests` (после
  перезакрепления сабмодуля на `0.8.0` он молча продолжал читать
  устаревший корпус `0.7` — путь был захардкожен, а не выведен из
  пина) и исполняет все категории корпуса, включая новую
  `strict-lossy/` (`loads()` обязан совпасть с lax-значением,
  `loadsStrict()` обязан выбросить исключение с соответствующей
  причиной, телом и канонической формой). Guard-тест обрушивает сборку
  при появлении нераспознанной категории в корпусе, чтобы это не
  повторилось молча.
- Унаследовано из невышедшего цикла 0.7 (история — в том же окне
  заменена пином 0.8.0): `rust-version` поднят до `1.71` (MSRV
  ktav 0.7); раннер начал исполнять категории `unrepresentable/` и
  `parseable-unrepresentable/`; вывод `emitCanonical` сверяется
  побайтово с companion-файлом `.canonical.ktav` каждой valid-фикстуры
  (spec § 5.9.10, § 5.9.8); guard заполненности корпуса стал покрывать
  все категории фикстур.

>>>>> lang=zh
- `crates/cabi` 改为单次调用 `ktav::declare_cabi!()`（ktav 的 `cabi`
  特性），取代手写的 C ABI 垫片；导出的符号集不变，因此 Java API
  不受影响。0.8.0 依赖下限和 `v0.8.0` spec 固定版本带来 § 5.2：
  带有多余前导零的十进制数解析为 String，而非 Integer。
- 包版本升至 **0.8.0**，与核心和规范同步；预编译库的回退下载现在指向
  `v0.8.0` 发布资产。
- Conformance 运行器读取 `spec/versions/0.8/tests`(子模块重新固定到
  `0.8.0` 之后，它一直静默读取过期的 `0.7` 语料——路径是硬编码的，
  并非从固定版本推导而来)，并执行语料中的每个类别，包括新增的
  `strict-lossy/`（`loads()` 必须等于 lax 值，`loadsStrict()` 必须以
  匹配的原因、body 与规范形式抛出异常）。一个 guard 测试会在语料中
  出现无法识别的类别目录时使构建失败，以防止这个问题再次悄然发生。
- 从未发布的 0.7 周期继承而来（历史 —— 在同一窗口内已被 0.8.0 固定
  取代）：`rust-version` 提升至 `1.71`（ktav 0.7 的 MSRV）；运行器
  开始执行 `unrepresentable/` 与 `parseable-unrepresentable/` 类别；
  `emitCanonical` 输出与每个 valid fixture 的 `.canonical.ktav`
  伴随文件逐字节比对（spec § 5.9.10、§ 5.9.8）；语料库填充度 guard
  开始覆盖所有 fixture 类别。

