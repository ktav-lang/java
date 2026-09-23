>>>>> lang=en
- Tracks `ktav 0.7.0` and spec 0.7.0; the spec submodule is pinned to
  `v0.7.0`.
- `rust-version` raised to `1.71` (the ktav 0.7 MSRV).
- Conformance tests now run the spec 0.7 corpus; invalid fixtures whose
  raw bytes are not valid UTF-8 (§ 6.15) are rejected at the strict-UTF-8
  test boundary, since the Java API takes a `String` and cannot receive
  such input.
- The conformance runner now executes the spec 0.7 `unrepresentable/`
  and `parseable-unrepresentable/` categories: writers must refuse the
  fixture values, canonical emit must refuse the parsed values, and the
  expected reason code must appear in the error message where this
  binding surfaces it.
- The conformance runner now checks `emitCanonical` output against every
  valid fixture's `.canonical.ktav` companion byte-for-byte (spec
  § 5.9.10, § 5.9.8) — previously the canonical writer was only checked
  for refusing unrepresentable values, never compared against the
  spec's own canonical bytes.
- The corpus-population guard now covers every fixture category (not
  just `unrepresentable`/`parseable-unrepresentable`), asserts each is
  non-empty, rejects an unknown fixture category directory, and asserts
  `valid/` ships as many `.canonical.ktav` companions as fixtures.

>>>>> lang=ru
- Binding отслеживает `ktav 0.7.0` и spec 0.7.0; подмодуль spec закреплён
  на `v0.7.0`.
- `rust-version` поднят до `1.71` (MSRV ktav 0.7).
- Конформанс-тесты выполняются на корпусе spec 0.7; invalid-фикстуры,
  чьи сырые байты не являются корректным UTF-8 (§ 6.15), отклоняются на
  границе теста строгим декодированием UTF-8 — Java API принимает
  `String` и не может получить такой ввод.
- Конформанс-раннер теперь выполняет категории spec 0.7
  `unrepresentable/` и `parseable-unrepresentable/`: writer обязан
  отклонить значение фикстуры, каноническая запись — отклонить
  разобранное значение, а ожидаемый код причины должен присутствовать
  в сообщении об ошибке там, где binding его пробрасывает.
- Конформанс-раннер теперь сверяет вывод `emitCanonical` побайтово с
  companion-файлом `.canonical.ktav` каждой valid-фикстуры (spec
  § 5.9.10, § 5.9.8) — ранее канонический writer проверялся только на
  отказ от unrepresentable-значений, но никогда не сравнивался с
  собственными каноническими байтами spec.
- Guard заполненности корпуса теперь покрывает все категории фикстур (а
  не только `unrepresentable`/`parseable-unrepresentable`), проверяет
  непустоту каждой, отклоняет неизвестную директорию категории и
  проверяет, что `valid/` содержит столько же companion-файлов
  `.canonical.ktav`, сколько фикстур.

>>>>> lang=zh
- 跟踪 `ktav 0.7.0` 与 spec 0.7.0；spec 子模块固定在 `v0.7.0`。
- `rust-version` 提升至 `1.71`（ktav 0.7 的 MSRV）。
- 一致性测试现在运行 spec 0.7 语料库；原始字节不是有效 UTF-8 的
  invalid fixture（§ 6.15）在测试边界通过严格 UTF-8 解码予以拒绝 ——
  Java API 接收 `String`，无法传入此类输入。
- 一致性测试运行器现在执行 spec 0.7 的 `unrepresentable/` 与
  `parseable-unrepresentable/` 类别：写入方必须拒绝 fixture 值，规范
  输出必须拒绝解析后的值，并且在该 binding 能透出预期原因码的场合，
  错误消息必须包含它。
- 一致性测试运行器现在会将 `emitCanonical` 的输出与每个 valid fixture
  的 `.canonical.ktav` 伴随文件逐字节比对（spec § 5.9.10、§ 5.9.8）——
  此前规范化 writer 只检查是否拒绝 unrepresentable 值，从未与 spec
  自身的规范字节做过比对。
- 语料库填充度 guard 现在覆盖所有 fixture 类别（不再只是
  `unrepresentable/`/`parseable-unrepresentable/`），断言每个类别非空，
  拒绝未知的 fixture 类别目录，并断言 `valid/` 下 `.canonical.ktav`
  伴随文件数量与 fixture 数量一致。

