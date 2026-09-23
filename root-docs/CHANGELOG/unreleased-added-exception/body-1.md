>>>>> lang=en
- **`KtavException` now carries the nine structured error fields** from
  the Rust core's error envelope: `getError()`, `getReason()`,
  `getLine()`, `getLineText()`, `getSpanStart()`, `getSpanEnd()`,
  `getPath()`, `getBody()`, `getCanonical()`, `getSpecSection()`.
  Absent information is `null` — which is why the numeric accessors
  return boxed `Long` rather than `long` — never a missing accessor.

  `getPath()` returns a `List<String>` of **exact decoded key segments,
  never a joined string**: a key literally named `a.b` is one segment
  and cannot be confused with a two-segment path.

  ```java
  try {
      Ktav.loadsStrict("version: 1.10\n");
  } catch (KtavException e) {
      e.getError();        // "LossyScalar"
      e.getBody();         // "1.10"
      e.getCanonical();    // "1.1"
      e.getSpecSection();  // "§3.6/§5.2"
  }
  ```

  The envelope names the two writer rejections apart:
  `"UnrepresentableAt"` when the writer can say where the offending
  node is (it also fills `getPath()`), `"Unrepresentable"` when it
  cannot. The `reason` code is identical in both, so a caller that only
  needs "the write was refused" matches on `getReason()`.

  The envelope is parsed by a streaming reader that never throws: an
  unrecognised payload degrades to a `Message`-class error rather than
  turning a diagnostic into a second failure.

>>>>> lang=ru
- **`KtavException` теперь несёт девять структурированных полей ошибки** из
  error envelope Rust-ядра: `getError()`, `getReason()`,
  `getLine()`, `getLineText()`, `getSpanStart()`, `getSpanEnd()`,
  `getPath()`, `getBody()`, `getCanonical()`, `getSpecSection()`.
  Отсутствующая информация — `null` — именно поэтому числовые аксессоры
  возвращают упакованный `Long`, а не `long`, — и никогда не отсутствующий
  аксессор.

  `getPath()` возвращает `List<String>` из **точных декодированных
  сегментов ключа, а не склеенную строку**: ключ, буквально названный
  `a.b`, — один сегмент, и его нельзя спутать с двухсегментным путём.

  ```java
  try {
      Ktav.loadsStrict("version: 1.10\n");
  } catch (KtavException e) {
      e.getError();        // "LossyScalar"
      e.getBody();         // "1.10"
      e.getCanonical();    // "1.1"
      e.getSpecSection();  // "§3.6/§5.2"
  }
  ```

  Конверт различает два отказа writer'а:
  `"UnrepresentableAt"` — когда writer может сказать, где находится
  проблемный узел (тогда он также заполняет `getPath()`), и
  `"Unrepresentable"` — когда не может. Код `reason` одинаков в обоих
  случаях, поэтому вызывающему, которому нужно лишь убедиться, что
  «запись отклонена», достаточно сравнивать `getReason()`.

  Конверт разбирает streaming reader, который никогда не бросает
  исключений: нераспознанная нагрузка деградирует до ошибки класса
  `Message`, вместо того чтобы превращать диагностику во вторую ошибку.

>>>>> lang=zh
- **`KtavException` 现在携带九个结构化错误字段**，来自 Rust 核心的
  error envelope：`getError()`、`getReason()`、`getLine()`、
  `getLineText()`、`getSpanStart()`、`getSpanEnd()`、`getPath()`、
  `getBody()`、`getCanonical()`、`getSpecSection()`。
  缺失的信息为 `null` —— 这正是数值访问器返回装箱 `Long` 而非 `long`
  的原因 —— 而绝不会是缺失的访问器。

  `getPath()` 返回 `List<String>`,内容是**精确解码后的键段，
  而非拼接字符串**：字面名为 `a.b` 的键是单个段，不会与两段路径混淆。

  ```java
  try {
      Ktav.loadsStrict("version: 1.10\n");
  } catch (KtavException e) {
      e.getError();        // "LossyScalar"
      e.getBody();         // "1.10"
      e.getCanonical();    // "1.1"
      e.getSpecSection();  // "§3.6/§5.2"
  }
  ```

  信封区分了 writer 的两种拒绝：`"UnrepresentableAt"` —— 当 writer
  能指出问题节点所在时（此时它还会填充 `getPath()`）；
  `"Unrepresentable"` —— 当不能时。两者的 `reason` 码相同，因此只需
  判断“写入被拒绝”的调用方匹配 `getReason()` 即可。

  信封由永不抛异常的 streaming reader 解析：无法识别的载荷会降级为
  `Message` 类错误，而不是把一次诊断变成第二次失败。

