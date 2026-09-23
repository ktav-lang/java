>>>>> lang=en
### Changed

- **Error messages have changed.** They are now reconstructed from the
  envelope's fields rather than passed through from the core's
  `Display` output. Callers matching on message strings will need to
  match on `getError()` / `getReason()` instead — which is the point of
  the change. `getMessage()` remains human-readable and is never the
  raw JSON.

- Minimum `ktav` core raised to **0.7.1**: `format_str` and
  `ErrorEnvelope` do not exist before it. In Cargo terms the
  requirement is `>=0.7.1, <0.8.0` — the floor rises, the ceiling stays
  inside 0.7.x.

- Migrated `crates/cabi` to a single `ktav::declare_cabi!()` invocation
  (ktav's `cabi` feature) instead of a hand-rolled C ABI shim; the
  exported symbol surface is unchanged, so the Java API is unaffected.
  Dependency floor raised to **0.8.0**, spec submodule re-pinned to
  `v0.8.0` (adds § 5.2: a decimal with a redundant leading zero parses
  as a String, not an Integer).
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

>>>>> lang=ru
### Изменено

- **Сообщения об ошибках изменились.** Теперь они реконструируются из
  полей конверта, а не пробрасываются из вывода `Display` ядра.
  Вызывающим, которые матчатся на строки сообщений, нужно будет матчиться
  на `getError()` / `getReason()` — в этом и смысл изменения.
  `getMessage()` остаётся человекочитаемым и никогда не является сырым
  JSON.

- Минимальная версия ядра `ktav` поднята до **0.7.1**: `format_str` и
  `ErrorEnvelope` не существуют до неё. В терминах Cargo требование —
  `>=0.7.1, <0.8.0` — нижняя граница растёт, верхняя остаётся внутри
  0.7.x.

- `crates/cabi` переведён на один вызов `ktav::declare_cabi!()` (фича
  `cabi` крейта ktav) вместо рукописной C ABI-прослойки; набор
  экспортируемых символов не изменился, поэтому Java API не затронут.
  Нижняя граница зависимости поднята до **0.8.0**, подмодуль spec
  перезакреплён на `v0.8.0` (добавлен § 5.2: десятичное число с
  избыточным ведущим нулём разбирается как String, а не Integer).
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

>>>>> lang=zh
### 变更

- **错误消息已更改。** 现在它们由信封的字段重建，而不是从核心的
  `Display` 输出透传。依赖消息字符串进行匹配的调用方需要改为匹配
  `getError()` / `getReason()` —— 这正是本次改动的目的。
  `getMessage()` 保持人类可读，且永远不是原始 JSON。

- 最低 `ktav` 核心版本提升至 **0.7.1**：`format_str` 与 `ErrorEnvelope`
  在此之前不存在。用 Cargo 的话说，要求为 `>=0.7.1, <0.8.0` ——
  下限抬高，上限仍留在 0.7.x 内。

- `crates/cabi` 改为单次调用 `ktav::declare_cabi!()`（ktav 的 `cabi`
  特性），取代手写的 C ABI 垫片；导出的符号集不变，因此 Java API
  不受影响。依赖下限提升至 **0.8.0**，spec 子模块重新固定到 `v0.8.0`
  （新增 § 5.2：带有多余前导零的十进制数解析为 String，而非 Integer）。
- 包版本升至 **0.8.0**，与核心和规范同步；预编译库的回退下载现在指向
  `v0.8.0` 发布资产。
- Conformance 运行器读取 `spec/versions/0.8/tests`(子模块重新固定到
  `0.8.0` 之后，它一直静默读取过期的 `0.7` 语料——路径是硬编码的，
  并非从固定版本推导而来），并执行语料中的每个类别，包括新增的
  `strict-lossy/`（`loads()` 必须等于 lax 值，`loadsStrict()` 必须以
  匹配的原因、body 与规范形式抛出异常）。一个 guard 测试会在语料中
  出现无法识别的类别目录时使构建失败，以防止这个问题再次悄然发生。

