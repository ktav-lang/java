# ktav — биндинги для Java

**Languages:** [English](README.md) · **Русский** · [简体中文](README.zh.md)

Java-биндинги к [формату конфигурации Ktav](https://github.com/ktav-lang/spec).
Тонкая обёртка над эталонным парсером на Rust, подгружаемая в runtime
через [JNA](https://github.com/java-native-access/jna) — **никакой
сборки JNI на стороне потребителя**, обычный Gradle/Maven просто
работает.

Требуется **JDK 17+**. Пока распространяется через GitHub Releases
(публикация в Maven Central — запланирована).

## Быстрый старт

`build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
    // пока мы не в Maven Central, забирайте JAR из
    // GitHub Release — пример см. в README.
}

dependencies {
    implementation("io.github.ktav-lang:ktav:0.1.0")
    implementation("net.java.dev.jna:jna:5.15.0")
}
```

```java
import lang.ktav.Ktav;
import lang.ktav.Value;

public class Example {
    public static void main(String[] args) {
        String src = """
                service: web
                port:i 8080
                ratio:f 0.75
                tls: true
                tags: [
                    prod
                    eu-west-1
                ]
                db.host: primary.internal
                db.timeout:i 30
                """;

        Value cfg = Ktav.loads(src);
        System.out.println(cfg);

        String text = Ktav.dumps(cfg);
        System.out.println(text);
    }
}
```

## API

| Функция | Назначение |
| --- | --- |
| `Ktav.loads(String) -> Value` | Разобрать Ktav-документ в дерево `Value`. |
| `Ktav.dumps(Value) -> String` | Отрендерить `Value` обратно в Ktav-текст. Верхний уровень должен быть `Obj`. |
| `Ktav.nativeVersion() -> String` | Версия подгруженного `ktav_cabi`. |

На любой ошибке парсинга/рендеринга бросается `KtavException` —
сообщение это UTF-8 строка от нативного парсера.

## Маппинг типов

Повторяет enum `Value` из Rust-крейта — один вариант на каждый
примитив Ktav, без лоссных приведений:

| Ktav             | вариант `Value`                                         |
| ---------------- | ------------------------------------------------------- |
| `null`           | `Value.Null.NULL`                                       |
| `true` / `false` | `Value.Bool`                                            |
| `:i <digits>`    | `Value.Int` (текстовая форма — произвольная точность, `toBigInteger()` / `toLong()`) |
| `:f <number>`    | `Value.Flt` (текстовая форма — точный round-trip, `toDouble()`) |
| bare scalar      | `Value.Str`                                             |
| `[ ... ]`        | `Value.Arr` (`List<Value>`)                             |
| `{ ... }`        | `Value.Obj` (`LinkedHashMap<String, Value>`, порядок вставки сохранён) |

Типизированные целые и float хранятся **как текст**, чтобы произвольная
точность (цифры сверх `long`) и точное представление десятичного числа
побайтово сохранялись между циклами parse/render.

## Как резолвится нативная библиотека

При первом вызове Java-библиотека ищет `ktav_cabi` в таком порядке:

1. **`$KTAV_LIB_PATH`** — абсолютный путь к локальной сборке. Полезно
   для разработки и air-gapped CI.
2. **Кэш пользователя** — `<userCache>/ktav-java/v<версия>/…`,
   скачанный предыдущим вызовом.
3. **Скачивание с GitHub Release** — соответствующий ассет тянется
   один раз с
   `github.com/ktav-lang/java/releases/download/v<версия>/<имя>`
   и кладётся в (2). На первом вызове после установки нужна сеть.

`<userCache>` это `%LOCALAPPDATA%` на Windows, `~/Library/Caches` на
macOS, `$XDG_CACHE_HOME` или `~/.cache` на Linux.

## Поддерживаемые платформы

- JDK 17+ (sealed interfaces).
- Собранные бинарники для: `linux/amd64`, `linux/arm64`, `darwin/amd64`,
  `darwin/arm64`, `windows/amd64`, `windows/arm64`.
- Линукс-дистрибутивы должны использовать glibc 2.17+ (дефолтная цель
  Rust). Поддержка Alpine (musl) — запланирована.

## Лицензия

MIT — см. [LICENSE](LICENSE).

Спецификация Ktav: [ktav-lang/spec](https://github.com/ktav-lang/spec).
Эталонный Rust-крейт: [ktav-lang/rust](https://github.com/ktav-lang/rust).
