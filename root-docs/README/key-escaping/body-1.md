>>>>> lang=en
## Key escaping

Since spec 0.6.4 a literal `.` or `:` inside a key segment is written
with a backslash:

| source | parses as |
| --- | --- |
| `a\.b: v` | `{ "a.b": "v" }` — the key is the single segment `a.b` |
| `a\:b: v` | `{ "a:b": "v" }` — the key contains a colon |
| `x.y\.z: v` | `{ "x": { "y.z": "v" } }` — split on the first dot only; the escaped dot does not split |

A literal backslash in a key is `\\`.

>>>>> lang=ru
## Экранирование в ключах

Начиная со spec 0.6.4 литеральные `.` или `:` внутри сегмента ключа
записываются через backslash:

| источник | разбирается как |
| --- | --- |
| `a\.b: v` | `{ "a.b": "v" }` — ключ является одним сегментом `a.b` |
| `a\:b: v` | `{ "a:b": "v" }` — ключ содержит двоеточие |
| `x.y\.z: v` | `{ "x": { "y.z": "v" } }` — разделение только по первой точке; экранированная точка не разделяет |

Литеральный backslash в ключе пишется как `\\`.

>>>>> lang=zh
## 键的转义

自 spec 0.6.4 起,键段内的字面量 `.` 或 `:` 通过反斜杠书写:

| 写法 | 解析为 |
| --- | --- |
| `a\.b: v` | `{ "a.b": "v" }` —— 键是单个段 `a.b` |
| `a\:b: v` | `{ "a:b": "v" }` —— 键包含冒号 |
| `x.y\.z: v` | `{ "x": { "y.z": "v" } }` —— 仅在第一个点处分割；被转义的点不再分割 |

键中的字面量反斜杠写作 `\\`。

