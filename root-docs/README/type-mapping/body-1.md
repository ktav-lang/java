>>>>> lang=en
## Type mapping

Mirrors the Rust crate's `Value` enum — one variant per Ktav primitive,
no lossy coercions:

| Ktav             | `Value` variant                                         |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| bare integer     | `Value.Int` (text form — arbitrary precision, `toBigInteger()` / `toLong()`) |
| bare decimal     | `Value.Flt` (text form — exact round-trip, `toDouble()`) |
| other scalar     | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`, insertion order preserved) |

Integers and floats are held as **text** so arbitrary precision
(digits beyond `long`) and exact decimal round-trip are preserved byte
for byte across parse/render cycles.

>>>>> lang=ru
## Маппинг типов

Повторяет enum `Value` из Rust-крейта — один вариант на каждый
примитив Ktav, без лоссных приведений:

| Ktav             | вариант `Value`                                         |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| голое целое      | `Value.Int` (текстовая форма — произвольная точность, `toBigInteger()` / `toLong()`) |
| голое десятичное | `Value.Flt` (текстовая форма — точный round-trip, `toDouble()`) |
| прочий скаляр    | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`, порядок вставки сохранён) |

Целые и float хранятся **как текст**, чтобы произвольная
точность (цифры сверх `long`) и точное представление десятичного числа
побайтово сохранялись между циклами parse/render.

>>>>> lang=zh
## 类型映射

与 Rust crate 的 `Value` 枚举完全一致 —— Ktav 每个原语一个变体,
没有有损转换:

| Ktav             | `Value` 变体                                             |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| 裸整数           | `Value.Int`(文本形式 —— 任意精度,`toBigInteger()` / `toLong()`) |
| 裸小数           | `Value.Flt`(文本形式 —— 精确往返,`toDouble()`) |
| 其他标量         | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`,保留插入顺序) |

整数与浮点数以 **文本** 保存,从而任意精度
(超过 `long` 的位数)以及十进制的精确表示都能在 parse/render
之间逐字节保持一致。

