>>>>> lang=en
`getPath()` returns a `List<String>` of **exact decoded key segments,
never a joined string**: a key literally named `a.b` is one segment and
cannot be confused with a two-segment path.

Two writer rejections are named apart — `"UnrepresentableAt"` when the
writer can say which node is at fault (it fills `getPath()` too), and
`"Unrepresentable"` when it cannot. The `reason` code is the same in
both, so matching on `getReason()` is enough when you only need to know
that a write was refused.

>>>>> lang=ru
`getPath()` возвращает `List<String>` **точных декодированных сегментов
ключа, а не склеенную строку**: ключ, буквально названный `a.b`, — это
один сегмент, и его нельзя спутать с двухсегментным путём.

Два отказа writer'а названы по-разному: `"UnrepresentableAt"`, когда
writer может указать виновный узел (тогда он заполняет и `getPath()`), и
`"Unrepresentable"`, когда не может. Код `reason` у них одинаковый,
поэтому сопоставления по `getReason()` достаточно, если нужно лишь
знать, что запись отвергнута.

>>>>> lang=zh
`getPath()` 返回 `List<String>`，是**精确解码后的键段，绝不是拼接后的
字符串**：字面名为 `a.b` 的键是**一个**段，不可能与两段路径混淆。

writer 的两种拒绝被分开命名 —— 当 writer 能指出是哪个节点出错时用
`"UnrepresentableAt"`（此时它也会填充 `getPath()`），不能指出时用
`"Unrepresentable"`。两者的 `reason` 码相同，因此如果你只需要知道
"这次写入被拒绝了"，匹配 `getReason()` 就够了。

