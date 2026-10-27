>>>>> lang=en
## Unreleased

- Add pure-backend `insertValue` / `insertValueSegments`, `removeValue` / `removeValueSegments`, and `renameKey` / `renameKeySegments`, matching the native source-edit APIs. Insert requires an existing Object parent; remove retains empty ancestors; rename preserves value and order. Successful splices verify the intended tree, and unsafe source shapes fail with `EditUnsupported`. Insert/rename collisions report `EditAlreadyExists`. These APIs prepare 0.9; package versions are unchanged.

>>>>> lang=ru
## Unreleased

- Добавлены чистые `insertValue` / `insertValueSegments`, `removeValue` / `removeValueSegments` и `renameKey` / `renameKeySegments`, соответствующие нативному API. Вставка требует существующего Object-родителя, удаление сохраняет пустых предков, переименование сохраняет значение и порядок. Успешный сплайс проверяет ожидаемое дерево; небезопасная форма исходника даёт `EditUnsupported`. Коллизии вставки/переименования — `EditAlreadyExists`. Это подготовка 0.9; версии пакетов не изменены.

>>>>> lang=zh
## Unreleased

- 新增纯后端 `insertValue` / `insertValueSegments`、`removeValue` / `removeValueSegments` 和 `renameKey` / `renameKeySegments`，对应原生源文本编辑 API。插入要求已存在的 Object 父节点，删除保留空祖先，重命名保留值和顺序。成功拼接验证预期树，无法安全处理的源形式返回 `EditUnsupported`；插入或重命名冲突返回 `EditAlreadyExists`。这些 API 为 0.9 做准备，包版本不变。

