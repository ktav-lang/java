>>>>> lang=en
### Formatting

`Ktav.format()` takes Ktav **source text** and returns Ktav source
text — it is not a `Value` renderer. It normalises structure to
canonical form (§ 5.9) while keeping the trivia the canonical writer
drops:

```java
System.out.print(Ktav.format("## the server\nserver: {host: a, port: 80}\n"));
// ## the server
// server: {
//     host: a
//     port: 80
// }
```

Every comment survives verbatim — Ktav has no trailing comments (§ 3.4:
a comment owns a whole line), so attachment is unambiguous. Blank lines
survive as a grouping hint, but a run of two or more collapses to
exactly one and blank padding just inside a bracket is dropped, which
makes the transform a fixed point: formatting already-formatted text
changes nothing. Key order is never changed — canonical form has no
sorting rule, and reordering keys would make review diffs worse.

For a document with no comments **and no blank lines** the result
equals `Ktav.emitCanonical(Ktav.loads(src))`. The stronger condition is
deliberate: blank lines are no more part of the `Value` model than
comments are, so the canonical writer drops them and `format` does not.

>>>>> lang=ru
### Форматирование

`Ktav.format()` принимает Ktav-**исходный текст** и возвращает
Ktav-исходный текст — это не рендерер `Value`. Он приводит структуру к
канонической форме (§ 5.9), сохраняя ту тривию, которую канонический
writer отбрасывает:

```java
System.out.print(Ktav.format("## the server\nserver: {host: a, port: 80}\n"));
// ## the server
// server: {
//     host: a
//     port: 80
// }
```

Каждый комментарий переживает форматирование дословно — в Ktav нет
замыкающих комментариев (§ 3.4: комментарий занимает строку целиком),
поэтому привязка однозначна. Пустые строки выживают как подсказка
группировки, но серия из двух и более схлопывается ровно в одну, а
пустая отбивка сразу внутри скобки убирается — это и делает
преобразование неподвижной точкой: форматирование уже отформатированного
текста ничего не меняет. Порядок ключей не меняется — в канонической
форме нет правила сортировки, а перестановка ключей только ухудшила бы
диффы при ревью.

Для документа без комментариев **и без пустых строк** результат равен
`Ktav.emitCanonical(Ktav.loads(src))`. Усиленное условие намеренно:
пустые строки входят в модель `Value` не больше, чем комментарии,
поэтому канонический writer их отбрасывает, а `format` — нет.

>>>>> lang=zh
### 格式化

`Ktav.format()` 接受 Ktav **源文本**并返回 Ktav 源文本 —— 它不是
`Value` 渲染器。它把结构规范化为规范形式（§ 5.9），同时保留规范
writer 会丢弃的那部分附属内容：

```java
System.out.print(Ktav.format("## the server\nserver: {host: a, port: 80}\n"));
// ## the server
// server: {
//     host: a
//     port: 80
// }
```

每条注释都逐字保留 —— Ktav 没有行尾注释（§ 3.4：注释独占一整行），
因此归属毫无歧义。空行作为分组提示保留下来，但连续两行及以上会合并为
恰好一行，紧贴括号内侧的空行填充会被丢弃，这正是该变换成为不动点的
原因：对已格式化的文本再次格式化不会有任何改变。键顺序绝不改变 ——
规范形式没有排序规则，而重排键只会让评审的 diff 更难读。

对于既没有注释**也没有空行**的文档，结果等同于
`Ktav.emitCanonical(Ktav.loads(src))`。这个更强的条件是有意的：空行
与注释一样都不属于 `Value` 模型，所以规范 writer 会丢弃它们，而
`format` 不会。

