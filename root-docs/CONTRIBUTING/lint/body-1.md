>>>>> lang=en
### Lint

```bash
./gradlew :lib:compileJava                       # javac warnings as-is
cargo fmt --all --check
cargo clippy --release -p ktav-cabi -- -D warnings
```

CI runs the same commands; run them locally before pushing.

>>>>> lang=ru
### Линт

```bash
./gradlew :lib:compileJava                       # javac warnings as-is
cargo fmt --all --check
cargo clippy --release -p ktav-cabi -- -D warnings
```

CI гоняет те же команды; прогоняйте их локально перед пушем.

>>>>> lang=zh
### Lint

```bash
./gradlew :lib:compileJava                       # javac warnings as-is
cargo fmt --all --check
cargo clippy --release -p ktav-cabi -- -D warnings
```

CI 运行相同的命令；推送前请先在本地运行。

