>>>>> lang=en
## Release flow

Tag `v<X.Y.Z>` on `main`. The release workflow cross-compiles six
platform binaries (`linux` amd64/arm64, `darwin` amd64/arm64, `windows`
amd64/arm64) plus builds the library JAR, and attaches all of them as
GitHub Release assets. The `LIB_VERSION` constant in
`NativeLoader.java` must match the tag — change it in the same commit
as the tag message.

>>>>> lang=ru
## Процесс релиза

Тег `v<X.Y.Z>` на `main`. Release-workflow кросс-компилирует шесть
платформенных бинарей (`linux` amd64/arm64, `darwin` amd64/arm64,
`windows` amd64/arm64) плюс собирает библиотечный JAR — всё
прикрепляется к GitHub Release. Константа `LIB_VERSION` в
`NativeLoader.java` обязана совпадать с тегом — правьте в том же
коммите, что и сообщение тега.

>>>>> lang=zh
## 发布流程

在 `main` 上打 `v<X.Y.Z>` tag。Release workflow 交叉编译六个
平台二进制（`linux` amd64/arm64、`darwin` amd64/arm64、`windows`
amd64/arm64）并构建库 JAR，全部作为 GitHub Release 资产。
`NativeLoader.java` 中的 `LIB_VERSION` 必须与 tag 一致 —— 请在
打 tag 消息的同一提交里更新。

