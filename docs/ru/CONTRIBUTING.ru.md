# Как участвовать в ktav (Java)

**Языки:** [English](../CONTRIBUTING.md) · **Русский** · [简体中文](../zh/CONTRIBUTING.zh.md)

## Основные правила

### 1. Каждый баг-фикс поставляется с регрессионным тестом

Найдя баг, **до исправления** напишите тест, который его
воспроизводит — он **должен падать на `main`** и проходить после
фикса. Оба — в одном PR.

Тесты лежат под `lib/src/test/java/lang/ktav/`:

| Файл                   | Область                                             |
| ---------------------- | --------------------------------------------------- |
| `SmokeTest.java`       | Loads / Dumps happy paths, BigInteger, ошибки.      |
| `ConformanceTest.java` | Conformance против `ktav-lang/spec`.                |

### 2. Не переосмысливайте формат в биндинге

Эта Java-библиотека — сознательно тонкая обёртка. Поведение парсера
и формата — в Rust-крейте
([`ktav-lang/rust`](https://github.com/ktav-lang/rust)): правка там
обновляет все языковые биндинги одновременно. Здесь — только
**Java-specific эргономика** (дерево `Value`, JNA-лоадер, логика
кэша / скачивания).

Если правка требует изменения формата — сначала обсуждение в
[`ktav-lang/spec`](https://github.com/ktav-lang/spec).

### 3. Изменения публичного API помечаются по совместимости

Если трогаете экспорт из `lang.ktav`, укажите в PR:

- **semver-совместимо** (добавления, ослабления сигнатур, доки); или
- **semver-ломающее** (переименования / удаления, изменения сигнатур,
  ужесточения типов) — bump пойдёт в следующий MINOR пока pre-1.0.

Обновите CHANGELOG-юниты под `root-docs/CHANGELOG/` (все три блока
`>>>>> lang=`) в том же PR и перегенерируйте вывод.

### 4. Один концепт — один коммит

Коммиты атомарные: фикс вместе с тестом, фича вместе с тестами,
переименование — отдельно, рефакторинг — отдельно. `git log --oneline`
должен читаться как changelog. Без `feat:` / `fix:` — не conventional
commits здесь.

### 5. Нативная библиотека в lockstep с JAR

Константа `LIB_VERSION` в
`lib/src/main/java/lang/ktav/internal/NativeLoader.java` **обязана**
совпадать с git-тегом релиза. Если бампите версию — правьте
`LIB_VERSION` в том же коммите. Рассинхрон заставит потребителей
скачивать нативку, не соответствующую их коду.

## Dev-setup

Нужно:

- JDK **17+**.
- Rust-toolchain через [`rustup`](https://rustup.rs/). MSRV: **1.70**.
- `git`.

Gradle идёт вместе с wrapper'ом (`./gradlew`) — отдельно ставить
не нужно.

Раскладка для локальной разработки — Java загружает собранный Rust'ом
cdylib `ktav_cabi` через JNA. Клонируйте соседние репо или
инициализируйте submodule:

```
ktav-lang/
├── java/     ← this repo
├── rust/     ← sibling Rust crate (path dep for local dev)
└── spec/     ← conformance fixtures (git submodule at java/spec/)
```

Rust C ABI крейт (`crates/cabi/`) по умолчанию зависит от
опубликованного `ktav` на crates.io. Для локальных cross-repo правок
замените `workspace.dependencies.ktav` в `Cargo.toml` на
`{ path = "../rust" }`.

### Сборка

```bash
# 1. Build the native library for your host platform.
cargo build --release -p ktav-cabi

# 2. Point Java at it.
export KTAV_LIB_PATH="$PWD/target/release/libktav_cabi.so"   # Linux
#      ="$PWD/target/release/libktav_cabi.dylib"             # macOS
#      ="$PWD/target/release/ktav_cabi.dll"                  # Windows

# 3. For conformance tests, point at the spec submodule.
git submodule update --init
export KTAV_SPEC_ROOT="$PWD/spec/versions/0.8/tests"
```

### Тесты

```bash
./gradlew :lib:test                                        # full suite
./gradlew :lib:test --tests '*SmokeTest*'                  # filter by class
./gradlew :lib:test --tests '*ConformanceTest*'            # spec fixtures only
```

Если `KTAV_LIB_PATH` или `KTAV_SPEC_ROOT` не заданы, соответствующие
тесты **skip'аются**, а не падают — так что `./gradlew test` в
пустом чекауте остаётся зелёным.

### Линт

```bash
./gradlew :lib:compileJava                       # javac warnings as-is
cargo fmt --all --check
cargo clippy --release -p ktav-cabi -- -D warnings
```

CI гоняет те же команды; прогоняйте их локально перед пушем.

## Архитектурные заметки

- **Wire-формат.** Rust и Java обмениваются JSON через FFI-границу,
  с обёртками `{"$i":"..."}` / `{"$f":"..."}` для типизированных
  integer / float. Так сохраняются произвольная точность и различие
  Integer vs Float при кодировании / декодировании.
- **Владение памятью.** Rust аллоцирует выходной буфер; Java копирует
  его в `byte[]` и сразу вызывает `ktav_free` на Rust-стороне. Ни один
  буфер не живёт долго через FFI-границу.
- **Loader.** `lang.ktav.internal.NativeLib` dlopen'ит библиотеку
  один раз на процесс через `Native.load` из JNA. Путь резолвит
  `NativeLoader.resolve()` — env / кэш / скачивание.

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

## Философия

Девиз Ktav: **"будь другом конфига, а не экзаменатором."** Прежде чем
предлагать Java-фичу, спросите:

- Добавляет ли это новое правило в голове читателя?
- Может ли это жить в коде пользователя, а не в библиотеке?
- Не размывает ли это принцип "никакой магии с типами"?

Новые правила дорого обходятся. Отвергайте всё, что явно не принадлежит.

## Языковая политика

Репо участвует в org-wide три-языковой политике (EN / RU / ZH).
Каждый prose-файл живёт в трёх параллельных версиях — naming
convention и правило "обновлять все три в одном коммите" см. в
[`ktav-lang/.github/AGENTS.md`](https://github.com/ktav-lang/.github/blob/main/AGENTS.md).

### Лицензия вкладов

Если вы явно не заявите иное, любой вклад, намеренно отправленный
для включения в этот проект, в соответствии с определением лицензии
Apache-2.0, будет лицензирован на условиях **MIT OR Apache-2.0** без
каких-либо дополнительных условий или ограничений.
