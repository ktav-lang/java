>>>>> lang=en
### Errors

`KtavException` is thrown on any parse or render failure. Beyond a
human-readable `getMessage()`, it carries the nine other structured
fields of the core's error envelope through ten accessors — `span` is
split into `getSpanStart()` / `getSpanEnd()` rather than boxed into a
pair type. `getMessage()` IS the envelope's own tenth field, `message`,
taken verbatim — never reassembled from the other nine:

```java
try {
    Ktav.loadsStrict("version: 1.10\n");
} catch (KtavException e) {
    e.getError();        // "LossyScalar"
    e.getLine();         // 1
    e.getLineText();     // "version: 1.10"
    e.getBody();         // "1.10"  — as written
    e.getCanonical();    // "1.1"   — as it would be stored
    e.getSpecSection();  // "§3.6/§5.2"
}
```

The full set is `getError()`, `getReason()`, `getLine()`,
`getLineText()`, `getSpanStart()`, `getSpanEnd()`, `getPath()`,
`getBody()`, `getCanonical()`, `getSpecSection()`. Absent information
is `null` — the boxed `Long` return types exist for exactly that
reason — never a missing accessor, so any field can be read without
first checking the error class.

>>>>> lang=ru
### Ошибки

`KtavException` бросается на любой ошибке разбора или рендеринга. Помимо
человекочитаемого `getMessage()` оно несёт остальные девять
структурированных полей конверта ошибки ядра через десять аксессоров —
`span` разложен на `getSpanStart()` / `getSpanEnd()`, а не упакован в
отдельный тип-пару. `getMessage()` — это и есть десятое поле конверта,
`message`, взятое дословно, а не пересобранное из остальных девяти:

```java
try {
    Ktav.loadsStrict("version: 1.10\n");
} catch (KtavException e) {
    e.getError();        // "LossyScalar"
    e.getLine();         // 1
    e.getLineText();     // "version: 1.10"
    e.getBody();         // "1.10"  — as written
    e.getCanonical();    // "1.1"   — as it would be stored
    e.getSpecSection();  // "§3.6/§5.2"
}
```

Полный набор — `getError()`, `getReason()`, `getLine()`,
`getLineText()`, `getSpanStart()`, `getSpanEnd()`, `getPath()`,
`getBody()`, `getCanonical()`, `getSpecSection()`. Отсутствующая
информация — это `null` (именно ради этого возвращаются боксированные
`Long`), а не пропавший аксессор, поэтому любое поле можно прочитать, не
проверяя предварительно класс ошибки.

>>>>> lang=zh
### 错误

解析或渲染失败时抛出 `KtavException`。除了人类可读的 `getMessage()`
之外，它还通过十个访问器携带核心错误信封其余九个结构化字段 —— `span`
被拆成 `getSpanStart()` / `getSpanEnd()`，而不是装进一个成对类型。
`getMessage()` 正是信封的第十个字段 `message`，逐字取用，而不是从其余
九个字段重新拼装出来的：

```java
try {
    Ktav.loadsStrict("version: 1.10\n");
} catch (KtavException e) {
    e.getError();        // "LossyScalar"
    e.getLine();         // 1
    e.getLineText();     // "version: 1.10"
    e.getBody();         // "1.10"  — as written
    e.getCanonical();    // "1.1"   — as it would be stored
    e.getSpecSection();  // "§3.6/§5.2"
}
```

完整集合是 `getError()`、`getReason()`、`getLine()`、`getLineText()`、
`getSpanStart()`、`getSpanEnd()`、`getPath()`、`getBody()`、
`getCanonical()`、`getSpecSection()`。缺失的信息是 `null` —— 装箱的
`Long` 返回类型正是为此而存在 —— 而不是缺少某个访问器，因此读取任何
字段都无需先判断错误类别。

