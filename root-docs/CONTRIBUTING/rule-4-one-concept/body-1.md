>>>>> lang=en
### 4. One concept per commit

Commits should be atomic: a bug fix and its test together, a feature
and its tests together, a rename on its own, a refactor on its own.
`git log --oneline` should read like a changelog. Don't prefix commit
messages with `feat:` / `fix:` — no conventional commits here.

>>>>> lang=ru
### 4. Один концепт — один коммит

Коммиты атомарные: фикс вместе с тестом, фича вместе с тестами,
переименование — отдельно, рефакторинг — отдельно. `git log --oneline`
должен читаться как changelog. Без `feat:` / `fix:` — не conventional
commits здесь.

>>>>> lang=zh
### 4. 一个概念一次提交

提交要保持原子：bug 修复与其测试一起、新功能与其测试一起、
重命名单独、重构单独。`git log --oneline` 应当读起来像 changelog。
不要使用 `feat:` / `fix:` 前缀 —— 这里不走 conventional commits。

