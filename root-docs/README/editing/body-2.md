>>>>> lang=en
Paths are dotted (`key1.key2.0.key4`); inside a segment `\.` is a
literal dot and `\\` a literal backslash. `editValueSegments` takes
already-decoded literal segments instead — a numeric segment is a
string key inside an object and a zero-based index inside an array.
There is no create, remove, or rename: only replacement of an existing
value.

Errors are structured: `EditInvalidPath` (body: the UTF-8 byte offset
inside the path argument where the defect is), `EditNotFound` (missing
key, out-of-bounds index, descent through a scalar, empty segments list), and
`EditUnsupported` (body: the reason — e.g. a noncontiguous
dotted-key/reopen target, a structural replacement that would drop
interior comment lines, or a value without a lossless representation
in the target context). Source-level problems still surface as the ordinary parse
envelopes. The pure backend exposes the same operations as
`PureKtav.editValue` / `PureKtav.editValueSegments` with identical
output.

>>>>> lang=ru
Пути — через точку (`key1.key2.0.key4`); внутри сегмента `\.` —
literal точка, `\\` — literal backslash. `editValueSegments` принимает
уже декодированные literal-сегменты: числовой сегмент — это ключ
внутри объекта и zero-based индекс внутри массива. Создание, удаление
и переименование не поддерживаются — только замена существующего
значения.

Ошибки структурированы: `EditInvalidPath` (body: UTF-8 byte offset
внутри аргумента пути, где обнаружен дефект), `EditNotFound`
(отсутствующий ключ, индекс вне диапазона, спуск через скаляр, пустой
список сегментов) и `EditUnsupported` (body: причина — например noncontiguous-цель
из dotted-ключей и reopen, структурная замена, которая потеряла бы
комментарии внутри цели, или значение без lossless-представления в контексте цели).
Проблемы самого исходника по-прежнему приходят обычными parse
envelope'ами. Pure-бэкенд предоставляет те же операции —
`PureKtav.editValue` / `PureKtav.editValueSegments` — с идентичным
выводом.

>>>>> lang=zh
路径以点分隔（`key1.key2.0.key4`）；段内 `\.` 表示字面点号，`\\`
表示字面反斜杠。`editValueSegments` 直接接收已解码的字面段——数字段
在对象中是字符串键，在数组中是从零开始的索引。不支持创建、删除或重
命名——只支持替换已存在的值。

错误是结构化的：`EditInvalidPath`（body：路径参数中发现缺陷处的
UTF-8 字节偏移）、`EditNotFound`（缺少键、索引越界、穿过标量下降、
空路径段列表）和 `EditUnsupported`（body：原因——例如由 dotted 键与 reopen
拼成的非连续目标、会丢失容器内部注释的结构性替换，或无法在目标上下文中
无损表示的值）。源文本本身的问题仍以普通解析错误 envelope 报告。
纯 Java 后端提供相同操作——`PureKtav.editValue` /
`PureKtav.editValueSegments`，输出完全一致。

