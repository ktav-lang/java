>>>>> lang=en
## 0.8.1 — 2026-10-22

### Added

- **Pure-Java backend.** The new package `lang.ktav.pure`, behind the
  `PureKtav` facade, implements a full bidirectional backend in plain
  Java: `loads` / `loadsStrict` (over `byte[]` or `String`), `dumps`,
  `toStringForceStrings`, `emitCanonical`, `canonicalFromSource`, and
  the comment-preserving `format` — the same operation set as the
  native `Ktav`, producing the same `Value` tree and the same
  `KtavException` envelopes. Canonical output is pinned by
  `CrossBackendTest` in CI — that byte-for-byte contract holds for the
  reference writer; other writers follow the reference writer's
  documented layout. Pure mode never loads or downloads a
  native library; there is no global backend switch — the backend is
  chosen explicitly by the class you call. Limits mirror the core:
  nesting depth bounded by 256, i64 / binary64 scalar domains.
  Canonical floats follow § 5.9.8 (shortest round-trip); the writers
  are iterative (no emission depth limit) and `format` is LF-only.

### Changed

>>>>> lang=ru
## 0.8.1 — 2026-10-22

### Добавлено

- **Чисто-Java бэкенд.** Новый пакет `lang.ktav.pure` за фасадом
  `PureKtav` реализует полноценный двунаправленный бэкенд на чистом
  Java: `loads` / `loadsStrict` (над `byte[]` или `String`), `dumps`,
  `toStringForceStrings`, `emitCanonical`, `canonicalFromSource` и
  сохраняющий комментарии `format` — тот же набор операций, что и у
  нативного `Ktav`, с тем же деревом `Value` и теми же конвертами
  `KtavException`. Канонический вывод фиксируется `CrossBackendTest`
  в CI — этот побайтовый контракт относится к эталонному райтеру;
  остальные райтеры следуют задокументированной раскладке эталонного
  райтера. Чистый режим никогда не загружает и не скачивает
  нативную библиотеку; глобального переключателя бэкендов нет —
  бэкенд выбирается явно вызываемым классом. Ограничения зеркалят
  ядро: глубина вложенности ограничена 256, домены скаляров
  i64 / binary64. Канонические float следуют § 5.9.8 (кратчайшая
  обратимая запись); райтеры итеративны, вывод `format` — только LF.

### Изменено

>>>>> lang=zh
## 0.8.1 — 2026-10-22

### 新增

- **纯 Java 后端。** 新的 `lang.ktav.pure` 包由 `PureKtav` 门面提供，
  用纯 Java 实现了完整的双向后端：`loads` / `loadsStrict`（支持
  `byte[]` 或 `String`）、`dumps`、`toStringForceStrings`、
  `emitCanonical`、`canonicalFromSource` 以及保留注释的 `format`
  —— 与原生 `Ktav` 相同的操作集合，产出相同的 `Value` 树和相同的
  `KtavException` 信封。规范输出由 CI 中的 `CrossBackendTest` 钉死
  —— 该逐字节契约属于参考写入器；其他写入器遵循参考写入器已
  记录的布局。纯模式从不加载或下载原生库；没有全局后端
  开关 —— 后端由所调用的类显式选择。限制与核心一致：嵌套深度以 256
  为界，标量域为 i64 / binary64。规范浮点数遵循 § 5.9.8（最短可回读
  十进制）；写入器是迭代的（无输出深度限制）；`format` 输出仅 LF。

### 变更

