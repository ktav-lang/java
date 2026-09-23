>>>>> lang=en
### 1. Every bug fix ships with a regression test

When you find a bug, **before fixing it**, write a test that reproduces
it — the test **must fail on `main`** and pass after the fix. Include
both in the same PR.

Tests live under `lib/src/test/java/lang/ktav/`:

| File                 | Scope                                                      |
| -------------------- | ---------------------------------------------------------- |
| `SmokeTest.java`     | Loads / Dumps happy paths, BigInteger, error surface.      |
| `ConformanceTest.java` | Cross-language conformance against `ktav-lang/spec`.     |

>>>>> lang=ru
### 1. Каждый баг-фикс поставляется с регрессионным тестом

Найдя баг, **до исправления** напишите тест, который его
воспроизводит — он **должен падать на `main`** и проходить после
фикса. Оба — в одном PR.

Тесты лежат под `lib/src/test/java/lang/ktav/`:

| Файл                   | Область                                             |
| ---------------------- | --------------------------------------------------- |
| `SmokeTest.java`       | Loads / Dumps happy paths, BigInteger, ошибки.      |
| `ConformanceTest.java` | Conformance против `ktav-lang/spec`.                |

>>>>> lang=zh
### 1. 每个 bug 修复都附带回归测试

发现 bug 时，**在修复之前** 先写一个复现它的测试 —— 测试在
`main` 分支上 **必须失败**，修复之后才通过。两者放在同一个 PR。

测试位于 `lib/src/test/java/lang/ktav/`：

| 文件                   | 范围                                             |
| ---------------------- | ------------------------------------------------ |
| `SmokeTest.java`       | Loads / Dumps 主路径、BigInteger、错误面。       |
| `ConformanceTest.java` | 对齐 `ktav-lang/spec` 的一致性测试。             |

