>>>>> lang=en
### 3. Public API changes note compatibility

If you touch anything exported from `lang.ktav`, say in the PR
description whether it is:

- **semver-compatible** (additions, looser signatures, doc changes); or
- **semver-breaking** (renamed / removed items, changed signatures,
  tightened types) — in which case the version bump lands in the next
  MINOR while we are pre-1.0.

Update the CHANGELOG source units under `root-docs/CHANGELOG/` (all
three `>>>>> lang=` blocks) in the same PR and regenerate the output.

>>>>> lang=ru
### 3. Изменения публичного API помечаются по совместимости

Если трогаете экспорт из `lang.ktav`, укажите в PR:

- **semver-совместимо** (добавления, ослабления сигнатур, доки); или
- **semver-ломающее** (переименования / удаления, изменения сигнатур,
  ужесточения типов) — bump пойдёт в следующий MINOR пока pre-1.0.

Обновите CHANGELOG-юниты под `root-docs/CHANGELOG/` (все три блока
`>>>>> lang=`) в том же PR и перегенерируйте вывод.

>>>>> lang=zh
### 3. 公共 API 改动需标注兼容性

若动到 `lang.ktav` 的导出项，请在 PR 描述里说明：

- **semver 兼容**（新增、签名放宽、文档改动）；或
- **semver 破坏性**（重命名 / 删除、签名变更、类型收紧）——
  在 pre-1.0 阶段将会导致下一个 MINOR 递增。

在同一个 PR 中更新 `root-docs/CHANGELOG/` 下的 CHANGELOG 源单元
(全部三个 `>>>>> lang=` 块)并重新生成产物。

