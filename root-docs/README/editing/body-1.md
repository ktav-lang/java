>>>>> lang=en
### Editing source text

`Ktav.editValue()` replaces an **existing** value in Ktav source text
and returns new source text — a lossless, byte-level splice, not a
parse→render round-trip:

```java
String out = Ktav.editValue("port: 8080\nhost: db\n", "port",
        new Value.Int("9090"));
// port: 9090
// host: db
```

Only the target's bytes change. Comments, blank lines, key spelling
and order, the BOM, and the document's LF / CR / CRLF style outside the
effective edit are preserved exactly. Dotted, inline, and block forms
survive as they are written; inline wrappers expand only when the
replacement would hurt readability (a multiline string, line growth
past `max(100 Unicode scalar values, the old physical line length)`, or
a replacement needing many escape sequences), and only the touched
branch expands — untouched nested branches stay inline. A replacement
equal to the current value is a byte-identical no-op, even for
noncanonical scalar spellings.

>>>>> lang=ru
### Правка исходного текста

`Ktav.editValue()` заменяет **существующее** значение в Ktav-исходном
тексте и возвращает новый исходный текст — это lossless-сплайс на
уровне байтов, а не круг parse→render:

```java
String out = Ktav.editValue("port: 8080\nhost: db\n", "port",
        new Value.Int("9090"));
// port: 9090
// host: db
```

Меняются только байты цели. Комментарии, пустые строки, написание и
порядок ключей, BOM и стиль LF / CR / CRLF вне эффективной правки
сохраняются дословно. Dotted-, inline- и блочные формы переживают
правку как написано; inline-обёртки раскрываются только тогда, когда
замена ухудшает читаемость (многострочная строка, рост строки сверх
`max(100 Unicode scalar values, прежняя длина строки)`, замена,
требующая множества escape-последовательностей), и раскрывается только
затронутая ветвь — нетронутые вложенные ветви остаются inline. Замена,
равная текущему значению, — byte-identical no-op даже для
неканонических написаний скаляров.

>>>>> lang=zh
### 编辑源文本

`Ktav.editValue()` 替换 Ktav 源文本中**已存在**的值并返回新的源文本
—— 这是字节级的无损拼接（splice），而不是 parse→render 往返：

```java
String out = Ktav.editValue("port: 8080\nhost: db\n", "port",
        new Value.Int("9090"));
// port: 9090
// host: db
```

只有目标的字节会改变。注释、空行、键的拼写与顺序、BOM 以及有效编辑
范围之外的 LF / CR / CRLF 行尾风格都原样保留。dotted、inline 和块
形式按原样保留；仅当替换会损害可读性时（多行字符串、行长超过
`max(100 个 Unicode 标量值, 原物理行长)`、或需要大量转义序列的替换），
inline 包装才会展开，且只展开受影响的分支——未触及的嵌套分支保持
inline。与当前值相等的替换是字节完全一致的 no-op，即使标量拼写不
规范也一样。

