>>>>> lang=en
## 0.8.0 — 2026-09-24

### Added

- **`Ktav.format(String src)`** — a comment-preserving formatter over
  Ktav *source text*, not a `Value` renderer. It normalises the
  document's structural spelling to canonical form (§ 5.9) while
  keeping the trivia the canonical writer drops. Every comment survives
  verbatim; Ktav has no trailing comments (§ 3.4: a comment owns a
  whole line), so attachment is unambiguous. Blank lines survive as a
  grouping hint, but a run of two or more collapses to exactly one and
  blank padding immediately inside a bracket is dropped — which is what
  makes the transform a fixed point. Key order is never changed
  (canonical form has no sorting rule). For a document with no comments
  *and no blank lines* the result equals
  `Ktav.emitCanonical(Ktav.loads(src))`; the stronger condition is
  deliberate, since blank lines are no more part of the `Value` model
  than comments are.

  ```java
  Ktav.format("## the server\nserver: {host: a, port: 80}\n");
  // ## the server
  // server: {
  //     host: a
  //     port: 80
  // }
  ```

>>>>> lang=ru
## 0.8.0 — 2026-09-24

### Добавлено

- **`Ktav.format(String src)`** — форматтер, сохраняющий комментарии, над
  Ktav *source text*, а не рендерер `Value`. Он приводит структурное
  написание документа к каноничной форме (§ 5.9), сохраняя trivia,
  которые канонический writer отбрасывает. Каждый комментарий сохраняется
  дословно; в Ktav нет trailing-комментариев (§ 3.4: комментарий занимает
  целую строку), поэтому привязка однозначна. Пустые строки сохраняются
  как подсказка группировки, но серия из двух и более сворачивается ровно
  в одну, а пустые строки непосредственно внутри скобок отбрасываются —
  именно это делает преобразование фиксированной точкой. Порядок ключей
  никогда не меняется (в каноничной форме нет правила сортировки). Для
  документа без комментариев *и без пустых строк* результат равен
  `Ktav.emitCanonical(Ktav.loads(src))`; более сильное условие —
  намеренное, поскольку пустые строки не являются частью модели `Value`
  не в большей степени, чем комментарии.

  ```java
  Ktav.format("## the server\nserver: {host: a, port: 80}\n");
  // ## the server
  // server: {
  //     host: a
  //     port: 80
  // }
  ```

>>>>> lang=zh
## 0.8.0 — 2026-09-24

### 新增

- **`Ktav.format(String src)`** —— 保留注释的格式化器，作用于 Ktav
  *源文本*，而非 `Value` 渲染器。它将文档的结构性写法归一化为规范形式
  （§ 5.9），同时保留规范 writer 会丢弃的 trivia。每条注释都逐字保留；
  Ktav 没有行尾注释（§ 3.4：注释独占整行），因此归属毫无歧义。空行作为
  分组提示予以保留，但连续两个及以上会折叠为恰好一个，而紧贴括号内部的
  空行会被丢弃 —— 这正是使该转换成为不动点的原因。键顺序永不改变
  （规范形式没有排序规则）。对于既无注释*也无空行*的文档，结果等于
  `Ktav.emitCanonical(Ktav.loads(src))`；这个更强的条件是刻意为之，
  因为空行之于 `Value` 模型，并不比注释更有资格存在。

  ```java
  Ktav.format("## the server\nserver: {host: a, port: 80}\n");
  // ## the server
  // server: {
  //     host: a
  //     port: 80
  // }
  ```

