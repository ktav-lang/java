>>>>> lang=en
## 0.3.1 — 2026-05-10

### Added

- **Top-level Array support** (spec § 5.0.1, ktav 0.3.1) — first
  content line decides Object vs Array. `Ktav.loads` now returns
  `Value.Arr` when the document begins with array-item shapes (bare
  scalars, typed/raw markers, lone openers, multi-line openers); all
  prior 0.3.0-valid documents still parse to the same `Value`.
  `Ktav.dumps` accepts a `Value.Arr` at the top level as well.
- **`Ktav.toStringForceStrings(Value)`** — render any Value with every
  leaf scalar (typed integers `:i`, typed floats `:f`, booleans, and
  `null`) coerced to a String via the raw marker (`::`). Compounds
  preserve their structure. Useful for "everything is a string"
  dumps. Round-trips back through `loads` as the same set of String
  scalars.

### Changed

- **Picked up `ktav 0.3.1`** — tracks ktav 0.3.1 (additive). Spec
  submodule synced to `7256816` (spec 0.1.1).

### Native binary cache

- `NativeLoader.LIB_VERSION` bumped to `0.3.1`. Fresh download from the
  matching GitHub Release on first call after upgrade.

>>>>> lang=ru
## 0.3.1 — 2026-05-10

### Добавлено

- **Поддержка top-level массивов** (spec § 5.0.1, ktav 0.3.1) — первая
  строка контента решает, Object перед нами или Array. `Ktav.loads`
  теперь возвращает `Value.Arr`, когда документ начинается с форм
  элементов массива (голые скаляры, типизированные/raw-маркеры,
  одиночные опенеры, многострочные опенеры); все документы, валидные
  для 0.3.0, по-прежнему разбираются в тот же `Value`. `Ktav.dumps`
  теперь также принимает `Value.Arr` на верхнем уровне.
- **`Ktav.toStringForceStrings(Value)`** — рендерит любой Value,
  приводя каждый листовой скаляр (типизированные целые `:i`,
  типизированные float'ы `:f`, булевы и `null`) к String через
  raw-маркер (`::`). Составные значения сохраняют структуру. Полезно
  для дампов «всё в строках». Round-trip обратно через `loads`
  возвращает тот же набор String-скаляров.

### Изменено

- **Подхватили `ktav 0.3.1`** — отслеживает ktav 0.3.1 (аддитивно).
  Подмодуль spec синхронизирован на `7256816` (spec 0.1.1).

### Кэш нативных бинарников

- `NativeLoader.LIB_VERSION` поднят до `0.3.1`. Свежее скачивание из
  соответствующего GitHub Release при первом вызове после обновления.

>>>>> lang=zh
## 0.3.1 — 2026-05-10

### 新增

- **顶层 Array 支持** (spec § 5.0.1, ktav 0.3.1) —— 首行内容决定解析为
  Object 还是 Array。当文档以数组项形态开头(裸标量、类型化/raw 标记、
  单独的开始符、多行开始符)时,`Ktav.loads` 现在返回 `Value.Arr`;
  所有此前在 0.3.0 下有效的文档仍解析为相同的 `Value`。`Ktav.dumps`
  现在也接受顶层的 `Value.Arr`。
- **`Ktav.toStringForceStrings(Value)`** —— 渲染任意 Value,并将每个
  叶子标量(类型化整数 `:i`、类型化浮点 `:f`、布尔值与 `null`)通过
  raw 标记 (`::`) 强制转换为 String。复合值保持结构不变。适用于
  "一切皆字符串" 的转储。通过 `loads` 往返后仍得到同一组 String
  标量。

### 变更

- **已采用 `ktav 0.3.1`** —— 跟踪 ktav 0.3.1(增量变更)。spec 子模块
  已同步至 `7256816` (spec 0.1.1)。

### 原生二进制缓存

- `NativeLoader.LIB_VERSION` 已升至 `0.3.1`。升级后首次调用时从
  匹配的 GitHub Release 重新下载。

