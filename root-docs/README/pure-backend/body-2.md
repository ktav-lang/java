>>>>> lang=en
The facade mirrors the shared operations of `Ktav` and is designed to
produce the same `Value` tree and the same `KtavException` envelopes,
so code written against one backend reads unchanged against the other
for those operations. Source editing includes replacement, insertion,
removal, and literal-key renaming, with textual and decoded-segment paths.
Successful edits preserve untouched source and verify the intended tree;
unsupported source shapes fail with `EditUnsupported`. Insert and rename
collisions report `EditAlreadyExists`. See the source-edit contract above.
There is no
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
Фасад повторяет общие операции `Ktav` и спроектирован так, чтобы
давать то же дерево `Value` и те же конверты `KtavException`; для этих
операций код можно переносить между бэкендами без изменений. Редактирование
включает замену, вставку, удаление и переименование буквального ключа,
с текстовыми путями и декодированными сегментами. Успех сохраняет нетронутый
исходник и проверяет ожидаемое дерево; неподдерживаемая форма даёт
`EditUnsupported`. Коллизии вставки и переименования — `EditAlreadyExists`.
Контракт редактирования описан выше. Глобального переключателя
бэкендов нет: бэкенд выбирается явно — вызовом `Ktav` (нативный) или
`PureKtav` (чистый).

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
纯模式从不加载或下载原生库 —— 没有 JNA、没有 `ktav_cabi`、没有缓存
目录、没有网络。它适合离线（air-gapped）环境、没有预编译原生资产的
平台，以及任何在乎纯 JVM 依赖的场景。原生后端仍是按参考实现调优的
路径。

纯后端的限制：嵌套深度（多行复合框架、inline 递归、dotted 路径
长度）以 256 为界（`PureParser.MAX_DEPTH`）；标量位于 i64 / binary64
域，与原生核心完全一致。该限制只作用于解析——写入器是迭代的，
输出任意深度的树都没有深度限制。与原生后端逐字节一致的规范输出是由 `CrossBackendTest` 在 CI 中检查的契约；其余写入器遵循参考写入器的文档化布局
（4 空格缩进、插入顺序、§ 5.9.10 键形式选择）。

