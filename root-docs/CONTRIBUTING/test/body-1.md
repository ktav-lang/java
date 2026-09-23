>>>>> lang=en
### Test

```bash
./gradlew :lib:test                                        # full suite
./gradlew :lib:test --tests '*SmokeTest*'                  # filter by class
./gradlew :lib:test --tests '*ConformanceTest*'            # spec fixtures only
```

When either `KTAV_LIB_PATH` or `KTAV_SPEC_ROOT` is unset, the relevant
tests **skip / no-op** rather than fail — so `./gradlew test` in a bare
checkout stays green.

>>>>> lang=ru
### Тесты

```bash
./gradlew :lib:test                                        # full suite
./gradlew :lib:test --tests '*SmokeTest*'                  # filter by class
./gradlew :lib:test --tests '*ConformanceTest*'            # spec fixtures only
```

Если `KTAV_LIB_PATH` или `KTAV_SPEC_ROOT` не заданы, соответствующие
тесты **skip'аются**, а не падают — так что `./gradlew test` в
пустом чекауте остаётся зелёным.

>>>>> lang=zh
### 测试

```bash
./gradlew :lib:test                                        # full suite
./gradlew :lib:test --tests '*SmokeTest*'                  # filter by class
./gradlew :lib:test --tests '*ConformanceTest*'            # spec fixtures only
```

如果 `KTAV_LIB_PATH` 或 `KTAV_SPEC_ROOT` 未设置，相关测试会
**跳过** 而非失败 —— 因此在干净的检出中运行 `./gradlew test`
仍会保持绿色。

