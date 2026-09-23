>>>>> lang=en
### 2. Don't reinvent the format in the bindings

This Java library is deliberately a thin wrapper. Parser and format
behaviour belong in the Rust crate
([`ktav-lang/rust`](https://github.com/ktav-lang/rust)) — changing it
there updates every language binding at once. Only **Java-specific
ergonomics** (`Value` type tree, JNA loader, cache / download logic)
belong in this repo.

If your change requires a format change, start a discussion in
[`ktav-lang/spec`](https://github.com/ktav-lang/spec) first.

>>>>> lang=ru
### 2. Не переосмысливайте формат в биндинге

Эта Java-библиотека — сознательно тонкая обёртка. Поведение парсера
и формата — в Rust-крейте
([`ktav-lang/rust`](https://github.com/ktav-lang/rust)): правка там
обновляет все языковые биндинги одновременно. Здесь — только
**Java-specific эргономика** (дерево `Value`, JNA-лоадер, логика
кэша / скачивания).

Если правка требует изменения формата — сначала обсуждение в
[`ktav-lang/spec`](https://github.com/ktav-lang/spec).

>>>>> lang=zh
### 2. 不要在绑定里重造格式

这个 Java 库刻意保持为薄封装。解析器和格式行为属于 Rust crate
([`ktav-lang/rust`](https://github.com/ktav-lang/rust))—— 改那里
等于同步更新所有语言绑定。这里仅收 **Java 特定的人体工学**
(`Value` 类型树、JNA 加载器、缓存 / 下载逻辑)。

如果改动需要格式变更，先去
[`ktav-lang/spec`](https://github.com/ktav-lang/spec) 讨论。

