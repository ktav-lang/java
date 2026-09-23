>>>>> lang=en
## API

| Function | Purpose |
| --- | --- |
| `Ktav.loads(String) -> Value` | Parse a Ktav document into the `Value` tree. |
| `Ktav.loadsStrict(String) -> Value` | Parse with strict numeric spelling checks. |
| `Ktav.dumps(Value) -> String` | Render a `Value` back as Ktav text. Top-level must be an `Obj`. |
| `Ktav.toStringForceStrings(Value) -> String` | Render like `dumps`, but coerce every leaf scalar to a String. |
| `Ktav.emitCanonical(Value) -> String` | Render a `Value` as deterministic canonical form. |
| `Ktav.format(String) -> String` | Normalise a document's spelling, keeping comments. |
| `Ktav.canonicalFromSource(String) -> String` | Parse and re-emit as canonical Ktav in one call — `emitCanonical(loads(src))` with no intermediate `Value`. Drops comments and blank lines like `emitCanonical` does. |
| `Ktav.nativeVersion() -> String` | Version string reported by the loaded `ktav_cabi`. |

`toStringForceStrings` flattens integers, floats, booleans and `null` to
their textual form via the raw marker (`::`); objects and arrays keep
their structure, since only leaves are coerced. The result parses back
through `loads` as the same set of String scalars — useful when a
downstream consumer does not understand typed markers.

>>>>> lang=ru
## API

| Функция | Назначение |
| --- | --- |
| `Ktav.loads(String) -> Value` | Разобрать Ktav-документ в дерево `Value`. |
| `Ktav.loadsStrict(String) -> Value` | Разобрать документ со строгой проверкой записи чисел. |
| `Ktav.dumps(Value) -> String` | Отрендерить `Value` обратно в Ktav-текст. Верхний уровень должен быть `Obj`. |
| `Ktav.toStringForceStrings(Value) -> String` | Отрендерить как `dumps`, но привести каждый leaf-скаляр к String. |
| `Ktav.emitCanonical(Value) -> String` | Отрендерить `Value` в детерминированной канонической форме. |
| `Ktav.format(String) -> String` | Нормализовать написание документа, сохраняя комментарии. |
| `Ktav.canonicalFromSource(String) -> String` | Разобрать и заново вывести как канонический Ktav за один вызов — `emitCanonical(loads(src))` без промежуточного `Value`. Комментарии и пустые строки отбрасывает так же, как `emitCanonical`. |
| `Ktav.nativeVersion() -> String` | Версия подгруженного `ktav_cabi`. |

`toStringForceStrings` расплющивает целые, float, булевы и `null` в их
текстовую форму через сырой маркер (`::`); объекты и массивы сохраняют
структуру, потому что приводятся только листья. Результат разбирается
обратно через `loads` как тот же набор String-скаляров — полезно, когда
потребитель на выходе не понимает типизированных маркеров.

>>>>> lang=zh
## API

| 函数 | 作用 |
| --- | --- |
| `Ktav.loads(String) -> Value` | 将 Ktav 文档解析为 `Value` 树。 |
| `Ktav.loadsStrict(String) -> Value` | 使用严格数字词法检查解析文档。 |
| `Ktav.dumps(Value) -> String` | 将 `Value` 渲染回 Ktav 文本。顶层必须是 `Obj`。 |
| `Ktav.toStringForceStrings(Value) -> String` | 输出与 `dumps` 相同，但把每个叶子标量强制为 String。 |
| `Ktav.emitCanonical(Value) -> String` | 将 `Value` 渲染为确定性的规范形式。 |
| `Ktav.format(String) -> String` | 规范化文档的写法，同时保留注释。 |
| `Ktav.canonicalFromSource(String) -> String` | 一次调用完成解析并重新输出为规范 Ktav —— 相当于 `emitCanonical(loads(src))`,但中间没有 `Value`。像 `emitCanonical` 一样丢弃注释与空行。 |
| `Ktav.nativeVersion() -> String` | 已加载的 `ktav_cabi` 的版本字符串。 |

`toStringForceStrings` 把整数、float、布尔与 `null` 用原始标记(`::`)
压平为它们的文本形式；对象与数组保持自身结构，因为只有叶子会被强制。
结果经由 `loads` 解析回来仍是同一组 String 标量 —— 当下游消费方不理解
类型标记时，这很有用。

