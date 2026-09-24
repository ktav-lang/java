>>>>> lang=en
  The envelope is parsed by a streaming reader that never throws: an
  unrecognised payload degrades to a `Message`-class error rather than
  turning a diagnostic into a second failure.

- **`Ktav.canonicalFromSource(String src)`** — parses Ktav source and
  re-emits it in canonical form in a single native call:
  `emitCanonical(loads(src))` without the intermediate `Value` tree and
  its JSON-wire encode/decode round-trip. The two entry points agree
  byte for byte — verified over bigint integers, huge exponents, `-0.0`
  and long-mantissa floats, where the stored text form leaves nothing
  to diverge. Comments and blank lines do not survive, like
  `emitCanonical` itself.

### Changed

- **Error messages are now the core's own rendering.** `getMessage()`
  returns the envelope's `message` field verbatim — the same text the
  Rust crate and every other binding print for the same error — never
  a locally reassembled sentence and never raw JSON. Against a native
  library older than 0.8.0, which wrote no `message` field, the binding
  falls back to its own reconstruction. Callers matching on message
  strings should match `getError()` / `getReason()` instead — which is
  the point of the change.

- **The intermediate 0.7 cycle never shipped.** 0.7.0/0.7.1 existed only
  as internal milestones (ktav 0.7 tracking, `format_str`,
  `ErrorEnvelope`) and were superseded by this release; the dependency
  floor and the spec pin moved straight from 0.6.4 to **0.8.0**. There
  is no supported 0.7.x binding version and no v0.7.0 spec pin.

>>>>> lang=ru
  Конверт разбирает streaming reader, который никогда не бросает
  исключений: нераспознанная нагрузка деградирует до ошибки класса
  `Message`, вместо того чтобы превращать диагностику во вторую ошибку.

- **`Ktav.canonicalFromSource(String src)`** — разбирает Ktav-источник и
  заново выводит его в канонической форме за один нативный вызов:
  `emitCanonical(loads(src))` без промежуточного дерева `Value` и его
  кодирования/декодирования JSON-wire. Оба входа совпадают побайтово —
  проверено на bigint-целых, огромных экспонентах, `-0.0` и float с
  длинной мантиссой, где текстовая форма хранения оставляет нечему
  расходиться. Комментарии и пустые строки не выживают, как и у самого
  `emitCanonical`.

### Изменено

- **Сообщения об ошибках теперь — собственный рендеринг ядра.**
  `getMessage()` возвращает поле `message` конверта дословно — тот же
  текст, что Rust-крейт и любой другой биндинг печатают для той же
  ошибки, — а не локально пересобранную фразу и не сырой JSON. Против
  нативной библиотеки старше 0.8.0, которая поле `message` не писала,
  биндинг откатывается к собственной реконструкции. Вызывающим,
  которые матчатся на строки сообщений, нужно матчиться на
  `getError()` / `getReason()` — в этом и смысл изменения.

- **Промежуточный цикл 0.7 не выходил.** 0.7.0/0.7.1 существовали только
  как внутренние вехи (трекинг ktav 0.7, `format_str`, `ErrorEnvelope`)
  и были заменены этим релизом; нижняя граница зависимости и пин spec
  сдвинулись с 0.6.4 сразу на **0.8.0**. Поддерживаемой версии биндинга
  0.7.x нет, как и пина spec v0.7.0.

>>>>> lang=zh
  信封由永不抛异常的 streaming reader 解析：无法识别的载荷会降级为
  `Message` 类错误，而不是把一次诊断变成第二次失败。

- **`Ktav.canonicalFromSource(String src)`** —— 在单个原生调用中解析
  Ktav 源文本并重新输出为规范形式：相当于 `emitCanonical(loads(src))`，
  但没有中间的 `Value` 树及其 JSON-wire 编解码往返。两个入口逐字节一致
  —— 已在 bigint 整数、巨大指数、`-0.0` 与长尾数 float 上验证，文本
  形式的存储让任何分歧都无处产生。与 `emitCanonical` 本身一样，注释与
  空行不会被保留。

### 变更

- **错误消息现在是核心自己的渲染。** `getMessage()` 逐字返回信封的
  `message` 字段 —— 与 Rust crate 及所有其他绑定对同一错误打印的文本
  相同 —— 而不是本地重新拼装的句子，也不是原始 JSON。对于不写入
  `message` 字段的 0.8.0 之前原生库，绑定回退到自己的重构。依赖消息
  字符串匹配的调用方应改为匹配 `getError()` / `getReason()` ——
  这正是本次改动的目的。

- **中间的 0.7 周期从未发布。** 0.7.0/0.7.1 只是内部里程碑（跟踪
  ktav 0.7、`format_str`、`ErrorEnvelope`），并被本发布取代；依赖
  下限与 spec 固定版本从 0.6.4 直接跃迁到 **0.8.0**。不存在受支持的
  0.7.x 绑定版本，也不存在 v0.7.0 的 spec 固定。

