//! Thin C ABI wrapper around the `ktav` crate for consumption from Java
//! via JNA (dynamic loading, no JNI boilerplate on the caller side).
//!
//! The whole exported surface — `ktav_loads`, `ktav_loads_strict`,
//! `ktav_dumps`, `ktav_dumps_force_strings`, `ktav_emit_canonical`,
//! `ktav_format`, `ktav_canonical_from_source`, `ktav_edit_value`,
//! `ktav_edit_value_segments`, `ktav_insert_value`,
//! `ktav_insert_value_segments`, `ktav_remove_value`,
//! `ktav_remove_value_segments`, `ktav_rename_key`,
//! `ktav_rename_key_segments`, `ktav_free`, `ktav_version` and
//! `ktav_abi_version` — is expanded by the
//! `ktav::declare_cabi!()` macro (ktav's `cabi` feature); this crate
//! carries no logic of its own. The symbols are expanded here, in the
//! cdylib's own crate, because symbols defined in a dependency rlib
//! would not survive into a downstream cdylib.
//!
//! Wire format between Java and Rust is JSON (see `ktav::cabi` for the
//! tagged `{"$i": ...}` / `{"$f": ...}` wrappers that keep the
//! Integer/Float distinction and arbitrary precision lossless).
//! Ownership contract: every returned buffer — success payload or JSON
//! error envelope — is freed by the caller via `ktav_free(ptr, len)`
//! exactly once, with the length it was returned with. Return code is
//! `0` on success, `1` on error, with `out_err` holding the JSON error
//! envelope.
//!
//! Дополнительные license metadata статичны и не освобождаются через `ktav_free`.

ktav::declare_cabi!();

include!(concat!(env!("OUT_DIR"), "/native_notices.rs"));
