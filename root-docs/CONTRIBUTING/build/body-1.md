>>>>> lang=en
### Build

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

>>>>> lang=ru
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

>>>>> lang=zh
### 构建

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

