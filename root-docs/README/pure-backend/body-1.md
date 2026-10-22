>>>>> lang=en
## The pure-Java backend

Since 0.8.1 the library ships a second, pure-Java backend in the
`lang.ktav.pure` package, behind the `PureKtav` facade:

| Function | Purpose |
| --- | --- |
| `PureKtav.loads(byte[])` / `PureKtav.loads(String)` | Parse a Ktav document into the `Value` tree. The `byte[]` entries take raw bytes and validate UTF-8 (§ 6.15) before the grammar runs; error spans and line numbers are reported over the ORIGINAL raw bytes (they are not rebased after a leading BOM is skipped). |
| `PureKtav.loadsStrict(byte[])` / `PureKtav.loadsStrict(String)` | Parse with strict numeric spelling checks. |
| `PureKtav.dumps(Value)` | Render a `Value` back as Ktav text. Top-level must be an `Obj` or `Arr`. |
| `PureKtav.toStringForceStrings(Value)` | Render like `dumps`, but coerce every leaf scalar to a String. |
| `PureKtav.emitCanonical(Value)` | Render a `Value` as deterministic canonical form. Canonical floats follow § 5.9.8: the shortest round-trip decimal, scientific notation iff `abs >= 1e7` or `abs < 1e-2`. |
| `PureKtav.canonicalFromSource(String)` | Parse and re-emit as canonical Ktav in one call. |
| `PureKtav.format(String)` | Normalise a document's spelling, keeping comments. Output line endings are always LF-only (LF, CR, and CRLF inputs are all accepted). |
| `PureKtav.version()` | Version of the pure backend — the spec version it implements. |

The facade mirrors `Ktav` operation-for-operation and is designed to
produce the same `Value` tree and the same `KtavException` envelopes,
so code written against one backend reads unchanged against the other. There is no
global backend switch: you choose a backend explicitly by calling
`Ktav` (native) or `PureKtav` (pure).

Pure mode never loads or downloads a native library — no JNA, no
`ktav_cabi`, no cache directory, no network. It is the right choice for
air-gapped environments, platforms without a prebuilt native asset, and
anywhere a JVM-only dependency matters. The native backend remains the
reference-tuned path.

Limits of the pure backend: nesting depth (multi-line compound frames,
inline recursion, dotted-path length) is bounded by 256
(`PureParser.MAX_DEPTH`), and scalars live in the i64 / binary64
domains, exactly like the native core. The bound applies to PARSING
only — the writers are iterative and emit arbitrarily deep trees
without a depth limit. Byte-for-byte canonical equality with the
native backend is the contract enforced by `CrossBackendTest` in CI; the other writers follow the
reference writer's documented layout (4-space indent, insertion order,
§ 5.9.10 key form selection).

>>>>> lang=ru
## Чисто-Java бэкенд

Начиная с 0.8.1, библиотека содержит второй, чисто-Java бэкенд в
пакете `lang.ktav.pure` за фасадом `PureKtav`:

| Функция | Назначение |
| --- | --- |
| `PureKtav.loads(byte[])` / `PureKtav.loads(String)` | Разобрать Ktav-документ в дерево `Value`. Вариант с `byte[]` принимает сырые байты и валидирует UTF-8 (§ 6.15) до запуска грамматики; спаны ошибок и номера строк сообщаются по ИСХОДНЫМ сырым байтам (не сдвигаются после пропуска ведущего BOM). |
| `PureKtav.loadsStrict(byte[])` / `PureKtav.loadsStrict(String)` | Разобрать документ со строгой проверкой записи чисел. |
| `PureKtav.dumps(Value)` | Отрендерить `Value` обратно в Ktav-текст. Верхний уровень должен быть `Obj` или `Arr`. |
| `PureKtav.toStringForceStrings(Value)` | Отрендерить как `dumps`, но привести каждый leaf-скаляр к String. |
| `PureKtav.emitCanonical(Value)` | Отрендерить `Value` в детерминированной канонической форме. Канонические числа с плавающей точкой следуют § 5.9.8: кратчайшая десятичная запись с обратимым округлением, научная запись только при `abs >= 1e7` или `abs < 1e-2`. |
| `PureKtav.canonicalFromSource(String)` | Разобрать и заново вывести как канонический Ktav за один вызов. |
| `PureKtav.format(String)` | Нормализовать написание документа, сохраняя комментарии. Переводы строк в выводе — всегда только LF (на входе принимаются LF, CR и CRLF). |
| `PureKtav.version()` | Версия чистого бэкенда — реализованная им версия спецификации. |

Фасад повторяет `Ktav` операция в операцию и спроектирован так, чтобы
давать то же дерево `Value` и те же конверты `KtavException`, поэтому
код, написанный против одного бэкенда, против другого читается без
изменений. Глобального
переключателя бэкендов нет: бэкенд выбирается явно — вызовом `Ktav`
(нативный) или `PureKtav` (чистый).

Чистый режим никогда не загружает и не скачивает нативную библиотеку —
ни JNA, ни `ktav_cabi`, ни кэш-каталога, ни сети. Это правильный выбор
для air-gapped-сред, платформ без готового нативного ассета и всюду,
где важна JVM-only зависимость. Нативный бэкенд остаётся путём,
настроенным по эталонной реализации.

Ограничения чистого бэкенда: глубина вложенности (многострочные рамки
компаундов, inline-рекурсия, длина dotted-путей) ограничена 256
(`PureParser.MAX_DEPTH`), а скаляры живут в доменах i64 / binary64 —
в точности как в нативном ядре. Ограничение действует только при
ПАРСИНГЕ — райтеры итеративны и выводят деревья произвольной глубины
без лимита. Побайтовое каноническое равенство с нативным бэкендом —
это контракт, который тест `CrossBackendTest` проверяет в CI; остальные райтеры следуют
задокументированной раскладке эталонного райтера (отступ в 4 пробела,
порядок вставки, выбор формы ключей по § 5.9.10).

>>>>> lang=zh
## 纯 Java 后端

从 0.8.1 起，库在 `lang.ktav.pure` 包中随附第二个纯 Java 后端，
由 `PureKtav` 门面提供：

| 函数 | 作用 |
| --- | --- |
| `PureKtav.loads(byte[])` / `PureKtav.loads(String)` | 将 Ktav 文档解析为 `Value` 树。`byte[]` 入口接受原始字节，并在运行语法之前校验 UTF-8（§ 6.15）；错误区间和行号按原始字节（ORIGINAL raw bytes）报告，跳过前导 BOM 后不会重新偏移。 |
| `PureKtav.loadsStrict(byte[])` / `PureKtav.loadsStrict(String)` | 使用严格数字词法检查解析文档。 |
| `PureKtav.dumps(Value)` | 将 `Value` 渲染回 Ktav 文本。顶层必须是 `Obj` 或 `Arr`。 |
| `PureKtav.toStringForceStrings(Value)` | 输出与 `dumps` 相同，但把每个叶子标量强制为 String。 |
| `PureKtav.emitCanonical(Value)` | 将 `Value` 渲染为确定性的规范形式。规范浮点数遵循 § 5.9.8：最短可回读十进制表示，仅当 `abs >= 1e7` 或 `abs < 1e-2` 时使用科学计数法。 |
| `PureKtav.canonicalFromSource(String)` | 一次调用完成解析并重新输出为规范 Ktav。 |
| `PureKtav.format(String)` | 规范化文档写法，保留注释。输出的换行符始终只有 LF（输入接受 LF、CR 和 CRLF）。 |
| `PureKtav.version()` | 纯后端版本 —— 其实现的规范版本。 |

该门面逐操作镜像 `Ktav`，其设计目标是产出相同的 `Value` 树和相同的
`KtavException` 信封，因此针对一个后端编写的代码，换到另一个后端
无需改动。没有全局后端开关：后端通过显式调用选择 —— `Ktav`
（原生）或 `PureKtav`（纯）。

纯模式从不加载或下载原生库 —— 没有 JNA、没有 `ktav_cabi`、没有缓存
目录、没有网络。它适合离线（air-gapped）环境、没有预编译原生资产的
平台，以及任何在乎纯 JVM 依赖的场景。原生后端仍是按参考实现调优的
路径。

纯后端的限制：嵌套深度（多行复合框架、inline 递归、dotted 路径
长度）以 256 为界（`PureParser.MAX_DEPTH`）；标量位于 i64 / binary64
域，与原生核心完全一致。该限制只作用于解析——写入器是迭代的，
输出任意深度的树都没有深度限制。与原生后端逐字节一致的规范输出是由 `CrossBackendTest` 在 CI 中检查的契约；其余写入器遵循参考写入器的文档化布局
（4 空格缩进、插入顺序、§ 5.9.10 键形式选择）。

