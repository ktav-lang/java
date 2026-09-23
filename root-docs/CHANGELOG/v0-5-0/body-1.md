>>>>> lang=en
## 0.5.0 — 2026-05-27

Implements Ktav spec 0.5.0. Tracks ktav-rust 0.5.0.

### Breaking

- Typed markers `:i` / `:f` removed. Numbers, booleans, and `null` are
  inferred from the lexical form (spec §§ 3.6, 5.2). Write `key: 42` for
  Integer, `key: 3.14` for Float, `key:: 42` to keep a String.
- Comments now use `##` (own line). A single `#` byte is content, not a
  comment.
- Bare integers and floats no longer parse as String — `port: 8080` yields
  `Value.Int("8080")`, not `Value.Str("8080")`.
- Lone `{` / `[` on the first content line opens a multi-line root Object /
  Array; the 0.1.1 JSONL-style semantic is removed.
- Key segments are trimmed of leading/trailing whitespace.

### Added

- **Inline compounds** `{k: v, …}` / `[i, …]` (spec § 5.8) with trailing
  comma, mid-value brace literal (§ 5.8.5), and nesting depth limit of 128.
- **Eight escape sequences** in inline scalars: `\\`, `\,`, `\}`, `\]`,
  `\{`, `\[`, `\n`, `\r` (spec § 3.7).
- **`Ktav.emitCanonical(Value)`** — render a Value as the deterministic
  canonical Ktav form (spec § 7), via the new `ktav_emit_canonical` C ABI
  export.

### Changed

- License: MIT → MIT OR Apache-2.0 (`LICENSE-MIT` + `LICENSE-APACHE`).
- Spec submodule: v0.5.0.
- ktav-rust dependency: 0.5.0.
- Conformance tests now run against `spec/versions/0.5/tests/`.

>>>>> lang=ru
## 0.5.0 — 2026-05-27

Реализует Ktav spec 0.5.0. Отслеживает ktav-rust 0.5.0.

### Ломающие изменения

- Типизированные маркеры `:i` / `:f` удалены. Числа, булевы значения и
  `null` выводятся из лексической формы (spec §§ 3.6, 5.2). Пишите
  `key: 42` для Integer, `key: 3.14` для Float, `key:: 42`, чтобы
  сохранить String.
- Комментарии теперь используют `##` (отдельная строка). Одиночный байт
  `#` — контент, а не комментарий.
- Целые и дробные числа без маркеров больше не разбираются как String —
  `port: 8080` даёт `Value.Int("8080")`, а не `Value.Str("8080")`.
- Одиночный `{` / `[` на первой строке контента открывает многострочный
  корневой Object / Array; JSONL-семантика 0.1.1 удалена.
- Сегменты ключей обрезаются от начальных/конечных пробелов.

### Добавлено

- **Инлайн-составные значения** `{k: v, …}` / `[i, …]` (spec § 5.8) с
  завершающей запятой, литералом фигурной скобки в середине значения
  (§ 5.8.5) и пределом глубины вложенности 128.
- **Восемь escape-последовательностей** в инлайновых скалярах: `\`,
  `\,`, `\}`, `\]`, `\{`, `\[`, `\n`, `\r` (spec § 3.7).
- **`Ktav.emitCanonical(Value)`** — рендеринг Value в детерминированную
  каноническую форму Ktav (spec § 7) через новый экспорт C ABI
  `ktav_emit_canonical`.

### Изменено

- Лицензия: MIT → MIT OR Apache-2.0 (`LICENSE-MIT` + `LICENSE-APACHE`).
- Подмодуль spec: v0.5.0.
- Зависимость ktav-rust: 0.5.0.
- Конформанс-тесты теперь запускаются на `spec/versions/0.5/tests/`.

>>>>> lang=zh
## 0.5.0 — 2026-05-27

实现 Ktav spec 0.5.0。跟踪 ktav-rust 0.5.0。

### 破坏性变更

- 移除了类型标记 `:i` / `:f`。数字、布尔值与 `null` 依据词法形式推断
  （spec §§ 3.6、5.2）。写 `key: 42` 得 Integer，写 `key: 3.14` 得
  Float，写 `key:: 42` 保持 String。
- 注释现在使用 `##`（独占一行）。单个 `#` 字节是内容，而非注释。
- 裸整数与浮点数不再解析为 String —— `port: 8080` 得到的是
  `Value.Int("8080")`，而非 `Value.Str("8080")`。
- 首个内容行上单独的 `{` / `[` 打开多行根 Object / Array；0.1.1 的
  JSONL 式语义已移除。
- 键段会裁剪首尾空白。

### 新增

- **内联复合值** `{k: v, …}` / `[i, …]`（spec § 5.8），支持尾随逗号、
  值中部花括号字面量（§ 5.8.5）以及 128 的嵌套深度上限。
- 内联标量中的**八个转义序列**：`\`、`\,`、`\}`、`\]`、`\{`、`\[`、
  `\n`、`\r`（spec § 3.7）。
- **`Ktav.emitCanonical(Value)`** —— 将 Value 渲染为确定性的规范
  Ktav 形式（spec § 7），经由新增的 `ktav_emit_canonical` C ABI 导出。

### 变更

- 许可证：MIT → MIT OR Apache-2.0（`LICENSE-MIT` + `LICENSE-APACHE`）。
- spec 子模块：v0.5.0。
- ktav-rust 依赖：0.5.0。
- 一致性测试现在针对 `spec/versions/0.5/tests/` 运行。

