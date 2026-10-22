>>>>> lang=en
For Maven, exclude both `net.java.dev.jna:jna` and `com.fasterxml.jackson.core:jackson-core`; alternatively, use the library JAR directly. Pure-only runtime use requires JDK 17 and no third-party runtime libraries. Pure success and error paths use no JNA, Jackson, JUnit, native code, WASM, subprocesses, network access, or fallback. Native backend use requires its dependencies; retain normal dependency resolution for it.

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
В Maven исключите `net.java.dev.jna:jna` и `com.fasterxml.jackson.core:jackson-core`; альтернативно используйте JAR библиотеки напрямую. Для выполнения только чистого бэкенда нужен JDK 17, сторонние runtime-библиотеки не нужны. Успешные и ошибочные пути чистого бэкенда не используют JNA, Jackson, JUnit, нативный код, WASM, процессы, сеть или fallback. Нативному бэкенду нужны его зависимости; для него сохраняйте обычное разрешение зависимостей.

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
Maven 请同时排除 `net.java.dev.jna:jna` 和 `com.fasterxml.jackson.core:jackson-core`；也可直接使用本库 JAR。纯后端运行需要 JDK 17，不需要第三方运行时库。纯后端的成功和错误路径均不使用 JNA、Jackson、JUnit、原生代码、WASM、子进程、网络或回退。原生后端用法需要其依赖；请为其保留常规依赖解析。
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

