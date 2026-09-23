>>>>> lang=en
## Dev setup

You need:

- JDK **17+**.
- A Rust toolchain via [`rustup`](https://rustup.rs/). MSRV: **1.70**.
- `git`.

Gradle ships via the included wrapper (`./gradlew`); no separate
install needed.

Layout during development — the Java library loads the Rust-built
`ktav_cabi` cdylib via JNA. Clone the sibling spec repo (used by
conformance tests) next to this one or initialise the submodule:

```
ktav-lang/
├── java/     ← this repo
├── rust/     ← sibling Rust crate (path dep for local dev)
└── spec/     ← conformance fixtures (git submodule at java/spec/)
```

The Rust C ABI crate (`crates/cabi/`) depends on the published `ktav`
crate on crates.io by default. For local cross-repo edits, switch the
`workspace.dependencies.ktav` entry in `Cargo.toml` to
`{ path = "../rust" }`.

>>>>> lang=ru
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

>>>>> lang=zh
## 开发环境

你需要：

- JDK **17+**。
- 通过 [`rustup`](https://rustup.rs/) 安装的 Rust 工具链。MSRV:**1.70**。
- `git`。

Gradle 已随仓库附带 wrapper(`./gradlew`)，无需单独安装。

本地开发的目录布局 —— Java 通过 JNA 加载 Rust 构建的
`ktav_cabi` cdylib。克隆相邻仓库或初始化 submodule：

```
ktav-lang/
├── java/     ← this repo
├── rust/     ← sibling Rust crate (path dep for local dev)
└── spec/     ← conformance fixtures (git submodule at java/spec/)
```

Rust C ABI crate(`crates/cabi/`)默认依赖 crates.io 上发布的
`ktav`。本地跨仓库改动时，把 `Cargo.toml` 中
`workspace.dependencies.ktav` 改为 `{ path = "../rust" }`。

