>>>>> lang=en
## Release flow

Tag `v<X.Y.Z>` on `main`. The release workflow cross-compiles six
platform binaries (`linux` amd64/arm64, `darwin` amd64/arm64, `windows`
amd64/arm64) plus builds the library JAR, and attaches all of them as
GitHub Release assets. The `LIB_VERSION` constant in
`NativeLoader.java` must match the tag — bump it in the release-prep
commit that precedes the tag.

The same tag also publishes the JAR to Maven Central as
`io.github.ktav-lang:ktav` (Sonatype Central Portal, via the
`publish-maven` job in `release.yml`).

>>>>> lang=ru
## Процесс релиза

Тег `v<X.Y.Z>` на `main`. Release-workflow кросс-компилирует шесть
платформенных бинарей (`linux` amd64/arm64, `darwin` amd64/arm64,
`windows` amd64/arm64) плюс собирает библиотечный JAR — всё
прикрепляется к GitHub Release. Константа `LIB_VERSION` в
`NativeLoader.java` обязана совпадать с тегом — правьте её в
подготовительном коммите перед тегом.

Тот же тег публикует JAR и в Maven Central как
`io.github.ktav-lang:ktav` (Sonatype Central Portal, job `publish-maven`
в `release.yml`).

>>>>> lang=zh
## 发布流程

在 `main` 上打 `v<X.Y.Z>` tag。Release workflow 交叉编译六个
平台二进制（`linux` amd64/arm64、`darwin` amd64/arm64、`windows`
amd64/arm64）并构建库 JAR，全部作为 GitHub Release 资产。
`NativeLoader.java` 中的 `LIB_VERSION` 必须与 tag 一致 —— 请在打 tag 之前的发布准备提交中更新。

同一 tag 还会把 JAR 发布到 Maven Central（`io.github.ktav-lang:ktav`，
经 `release.yml` 的 `publish-maven` job，走 Sonatype Central Portal）。

