>>>>> lang=en
## 0.2.0 — 2026-05-07

### Changed (breaking)

- **Picked up `ktav 0.2.0`** — multi-line strings now serialize in the
  indented stripped `( ... )` form by default. `:f 42` accepts integer
  literals (parsed as `42.0`). See the
  [`ktav` crate CHANGELOG](https://github.com/ktav-lang/rust/blob/main/CHANGELOG.md#020--2026-05-07).

  Code comparing serialized output byte-for-byte to a baked-in
  `((...))` literal must be updated. Round-trip is unchanged.

### Spec

- spec submodule synced (typed_float_integer_body fixture; oracle 42.0).

>>>>> lang=ru
## 0.2.0 — 2026-05-07

### Изменено (ломающее)

- **Подхватили `ktav 0.2.0`** — многострочные строки теперь
  сериализуются в отступованную stripped-форму `( ... )` по
  умолчанию. `:f 42` принимает целые литералы (парсятся как `42.0`).
  См. [`ktav` crate CHANGELOG](https://github.com/ktav-lang/rust/blob/main/CHANGELOG.md#020--2026-05-07).

  Код, который побайтово сравнивает сериализованный вывод с зашитым
  литералом `((...))`, нужно обновить. Round-trip не изменился.

### Спецификация

- подмодуль spec синхронизирован (фикстура typed_float_integer_body;
  ожидаемое значение oracle 42.0).

>>>>> lang=zh
## 0.2.0 — 2026-05-07

### 变更（破坏性）

- **已采用 `ktav 0.2.0`** —— 多行字符串现在默认序列化为缩进的
  stripped `( ... )` 形式。`:f 42` 现在接受整数字面量(解析为
  `42.0`)。参见 [`ktav` crate CHANGELOG](https://github.com/ktav-lang/rust/blob/main/CHANGELOG.md#020--2026-05-07)。

  需要更新将序列化输出与内置 `((...))` 字面量逐字节比较的代码。
  往返转换不变。

### 规范

- spec 子模块已同步(fixture typed_float_integer_body;预期值 42.0)。

