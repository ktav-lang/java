>>>>> lang=en
- Documentation: the README intro and quick start no longer describe
  Maven Central publication as "planned" — `io.github.ktav-lang:ktav`
  is published there by the release workflow on every tag. The
  key-escaping examples no longer use trailing `//` comments, which are
  not Ktav syntax (a comment is a `##` line, § 3.4). `examples/basic`
  was rewritten without the removed `:i` / `:f` typed markers, and the
  `Value` Javadoc describes lexical numeric inference instead of the
  marker syntax.

### Fixed

- **`ScalarRoot` and `NonFiniteFloat` now surface their normative
  § 5.9.0 reason codes.** These two unrepresentable Values could never
  reach the native writers — the JSON-wire boundary refused them first
  as opaque `Message`-class errors — so `getReason()` came back `null`
  and the conformance runner exempted both reasons from its assertions.
  The binding now rejects them itself with the envelope the core's
  writers produce: `error` `UnrepresentableAt`, the spec reason, the
  decoded key `path` to the offending node (empty for `ScalarRoot`),
  `spec_section` `§ 5.9.0`, and the core's own message text.
  `toStringForceStrings` still coerces non-finite floats to Strings
  rather than rejecting them.
- The conformance runner implements the § 8.5 runner contract: it loads
  `manifest.json`, rejects any schema version it does not implement, enforces
  the closed category set and exact fixture counts, and honours the
  `raw_bytes` flag — the invalid-UTF-8 fixture is checked against its
  oracle's `InvalidUtf8` expectation at the strict byte-to-String
  boundary. Invalid fixtures now compare `expected_error` from their
  JSON oracle instead of only asserting that some exception is thrown.
  A missing `ktav_cabi` or spec submodule fails the corpus tests
  instead of emitting passing skip tests.

>>>>> lang=ru
- Документация: intro и quick start в README больше не описывают
  публикацию в Maven Central как «запланированную» —
  `io.github.ktav-lang:ktav` публикуется там release-workflow'ом с
  каждым тегом. Примеры экранирования в ключах больше не используют
  хвостовые комментарии `//`, которых нет в синтаксисе Ktav (комментарий
  — это строка `##`, § 3.4). `examples/basic` переписан без удалённых
  типизированных маркеров `:i` / `:f`, а Javadoc `Value` описывает
  вывод типа числа из лексической формы вместо синтаксиса маркеров.

### Исправлено

- **`ScalarRoot` и `NonFiniteFloat` теперь пробрасывают нормативные коды
  причин § 5.9.0.** Эти два unrepresentable-значения никогда не доходили
  до нативных writer'ов — граница JSON-wire отказывала им раньше как
  непрозрачным ошибкам класса `Message` — поэтому `getReason()`
  возвращал `null`, а conformance-раннер освобождал обе причины от
  проверок. Биндинг теперь сам отклоняет их с тем же конвертом, какой
  производят writer'ы ядра: `error` `UnrepresentableAt`, нормативная
  причина, декодированный ключевой `path` до проблемного узла (пустой
  для `ScalarRoot`), `spec_section` `§ 5.9.0` и собственный текст
  сообщения ядра. `toStringForceStrings` по-прежнему приводит
  нечисловые (non-finite) float к String, а не отклоняет их.
- Conformance-раннер реализует контракт раннера § 8.5: загружает
  `manifest.json`, отвергает любую не реализованную раннером версию схемы,
  следит за закрытым набором категорий и точным числом фикстур и
  учитывает флаг `raw_bytes` — invalid-UTF-8 фикстура сверяется с
  ожиданием `InvalidUtf8` из её оракула на строгой границе
  байты->String. Invalid-фикстуры теперь сравнивают `expected_error` из
  JSON-оракула, а не просто проверяют, что брошено какое-то исключение.
  Отсутствующий `ktav_cabi` или сабмодуль spec обрушивает корпусные
  тесты вместо выдачи фальшиво-проходных skip-тестов.

>>>>> lang=zh
- 文档：README 的简介与快速开始不再把 Maven Central 发布描述为
  “已规划” —— release workflow 会在每个 tag 发布
  `io.github.ktav-lang:ktav`。键转义示例不再使用行尾 `//` 注释 ——
  这不是 Ktav 的语法（注释是独占一行的 `##`，§ 3.4）。`examples/basic`
  已重写，去掉了已移除的 `:i` / `:f` 类型标记；`Value` 的 Javadoc
  描述的是从词法形式推断数字类型，而非标记语法。

### 修复

- **`ScalarRoot` 与 `NonFiniteFloat` 现在会透出其规范的 § 5.9.0 原因码。**
  这两种 unrepresentable 值此前根本到不了原生 writer —— JSON-wire 边界
  会先把它们当作不透明的 `Message` 类错误拒绝 —— 因此 `getReason()`
  返回 `null`，conformance 运行器也把这两种原因排除在断言之外。绑定
  现在自行拒绝它们，并给出与核心 writer 相同的信封：`error`
  `UnrepresentableAt`、规范原因、指向问题节点的解码键 `path`
  （`ScalarRoot` 时为空）、`spec_section` `§ 5.9.0`，以及核心自己的
  消息文本。`toStringForceStrings` 仍然把非有限 float 强制为 String，
  而不是拒绝。
- Conformance 运行器实现了 § 8.5 运行器契约：加载 `manifest.json`，
  拒绝一切它未实现的 schema 版本，强制执行封闭的类别集合与精确的
  fixture 数量，并遵循 `raw_bytes` 标志 —— invalid-UTF-8 fixture 在
  严格的字节->String 边界上对照其 oracle 的 `InvalidUtf8` 预期进行
  校验。Invalid fixture 现在会比较 JSON oracle 中的 `expected_error`，
  而不是只断言“抛出了某个异常”。缺失 `ktav_cabi` 或 spec 子模块时，
  语料测试会失败，而不是发出伪装通过的 skip 测试。

