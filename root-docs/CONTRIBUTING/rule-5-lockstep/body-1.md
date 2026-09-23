>>>>> lang=en
### 5. Native library stays in lockstep with the JAR

The `LIB_VERSION` constant in
`lib/src/main/java/lang/ktav/internal/NativeLoader.java` **must** match
the git tag used to cut the release. If you bump the library version,
update `LIB_VERSION` in the same commit. Mismatched values cause
consumers to download a native library that doesn't match their code.

>>>>> lang=ru
### 5. Нативная библиотека в lockstep с JAR

Константа `LIB_VERSION` в
`lib/src/main/java/lang/ktav/internal/NativeLoader.java` **обязана**
совпадать с git-тегом релиза. Если бампите версию — правьте
`LIB_VERSION` в том же коммите. Рассинхрон заставит потребителей
скачивать нативку, не соответствующую их коду.

>>>>> lang=zh
### 5. 原生库与 JAR 步调一致

`lib/src/main/java/lang/ktav/internal/NativeLoader.java` 中的
`LIB_VERSION` 常量 **必须** 与发布用的 git tag 相同。升级版本时，
请在同一提交里更新 `LIB_VERSION`。不一致会让使用方下载到与代码
不匹配的原生库。

