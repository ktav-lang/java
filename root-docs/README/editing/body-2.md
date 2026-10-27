>>>>> lang=en
Both `Ktav` and standalone `PureKtav` provide `insertValue`,
`removeValue`, and `renameKey`, plus their `*Segments` variants, with
String and byte-array sources. Insertion creates only a new final key
under an existing Object parent: no overwrite, implicit parents, or
Array insertion. Removal deletes an existing value/subtree or Array
element, shifts remaining elements, and never prunes empty ancestors.
Renaming changes only the final Object key to a literal name, preserving
value and order; the same name is a byte-identical no-op. Array indexes
cannot be renamed. Replacement still changes only an existing value.

Edits splice source ranges rather than serialize the whole document.
Every success is reparsed and checked against the intended tree;
untouched bytes, comments, BOM, EOL/EOF conventions, and key spelling
remain unchanged. Inline layout expands only where needed for multiline
values, growth beyond max(100 Unicode scalars, original line width), or
String readability (at least four escapes and 25% overhead). Unsupported
source shapes fail explicitly rather than returning corrupted text.

Errors: `EditInvalidPath` (body: UTF-8 path byte offset), `EditNotFound`
(missing target/parent, scalar descent, invalid Array index, empty decoded
path), `EditAlreadyExists` (insert or rename collision), and
`EditUnsupported` (body: reason, including noncontiguous dotted targets
or removal of a compound containing comments). Source parse errors win
over edit validation. Pure mode has no native dependency or fallback.

>>>>> lang=ru
`Ktav` и самостоятельный `PureKtav` предоставляют `insertValue`,
`removeValue`, `renameKey` и варианты `*Segments` для String и byte[].
Вставка создаёт только новый конечный ключ существующего Object:
без перезаписи, неявных родителей или вставки в Array. Удаление убирает
существующее значение/поддерево либо элемент Array, сдвигает оставшиеся
элементы и не удаляет пустых предков. Переименование меняет только
последний ключ Object на буквальное имя, сохраняя значение и порядок;
прежнее имя даёт побайтовый no-op. Индексы Array не переименовываются.
Замена по-прежнему изменяет только существующее значение.

Правки выполняются сплайсом диапазонов, а не сериализацией документа.
Каждый успешный результат повторно разбирается и сверяется с ожидаемым
деревом; нетронутые байты, комментарии, BOM, EOL/EOF и написание ключей
сохраняются. Inline-раскладка раскрывается лишь по необходимости:
многострочное значение, рост сверх max(100 Unicode-скаляров, исходная
ширина строки) либо читаемость String (не менее четырёх экранирований
и 25% накладных расходов). Неподдерживаемая форма исходника даёт явный
отказ, а не повреждённый текст.

Ошибки: `EditInvalidPath` (body: UTF-8 byte offset пути), `EditNotFound`
(нет цели/родителя, спуск через скаляр, неверный индекс Array, пустой
декодированный путь), `EditAlreadyExists` (коллизия вставки/переименования)
и `EditUnsupported` (body: причина, включая несмежную dotted-цель или
удаление контейнера с комментариями). Ошибка разбора исходника имеет
приоритет. Чистому режиму не нужны native-зависимости или fallback.

>>>>> lang=zh
`Ktav` 和独立的 `PureKtav` 均提供 `insertValue`、`removeValue`、
`renameKey` 及 `*Segments` 变体，支持 String 和字节数组源文本。
插入仅在已存在的 Object 父节点中创建新的末段键，不覆盖现有键，
不隐式创建父节点，也不插入 Array。删除移除现有值、子树或 Array
元素，后续元素前移，但不裁剪空祖先。重命名仅将末段 Object 键改为
字面名称，保留值与顺序；名称不变时逐字节不变。不能重命名 Array
索引。替换仍仅修改现有值。

编辑拼接源文本范围，不重新序列化整个文档。每个成功结果都会重新
解析并核对预期树；未触及的字节、注释、BOM、EOL/EOF 约定和键拼写
保持不变。仅在多行值、增长超过 max(100 个 Unicode 标量，原行宽)
或 String 可读性需要时展开 inline 布局；可读性阈值为至少四个转义
且开销至少 25%。不支持的源文本形式会明确失败，不返回损坏文本。

错误包括 `EditInvalidPath`（body：路径的 UTF-8 字节偏移）、
`EditNotFound`（目标或父节点不存在、穿过标量、无效 Array 索引、
空解码路径）、`EditAlreadyExists`（插入或重命名冲突）和
`EditUnsupported`（body：原因，包括不连续 dotted 目标或删除含注释
的容器）。源文本解析错误优先。纯模式不依赖原生库，也不会回退。

