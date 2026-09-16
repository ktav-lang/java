//! C ABI wrapper around the `ktav` Rust crate, designed for consumption
//! from Java via JNA (dynamic loading, no JNI boilerplate on the caller side).
//!
//! ## Wire format
//!
//! Between Java and Rust we exchange **JSON**, not a custom binary. This
//! keeps the FFI boundary tiny and lets each side use
//! its native JSON machinery.
//!
//! Ktav's Integer and Float scalars do not map 1:1 onto JSON numbers
//! (JSON cannot represent arbitrary-precision integers, and loses the
//! Integer/Float distinction). To keep round-trips lossless we use
//! tagged wrappers:
//!
//! - `Value::Integer(s)` ⇄ `{"$i": "<digits>"}`
//! - `Value::Float(s)`   ⇄ `{"$f": "<text>"}`
//!
//! Everything else maps to the obvious JSON shape (`null`, booleans,
//! strings, arrays, objects). Object key order is preserved on both
//! sides (`indexmap` here, `LinkedHashMap` on the Java side).
//!
//! ## C ABI
//!
//! Eight functions, all use the same "caller-owned pointer, callee-owned
//! buffer" pattern:
//!
//! - `ktav_loads(src, src_len, out_buf, out_len, out_err, out_err_len) -> i32`
//! - `ktav_loads_strict(src, src_len, out_buf, out_len, out_err, out_err_len) -> i32`
//! - `ktav_dumps(src, src_len, out_buf, out_len, out_err, out_err_len) -> i32`
//! - `ktav_dumps_force_strings(src, src_len, out_buf, out_len, out_err, out_err_len) -> i32`
//! - `ktav_emit_canonical(src, src_len, out_buf, out_len, out_err, out_err_len) -> i32`
//! - `ktav_format(src, src_len, out_buf, out_len, out_err, out_err_len) -> i32`
//! - `ktav_free(ptr, len)` — free a buffer returned by any of the above.
//! - `ktav_version()` — NUL-terminated static string, for sanity checks.
//!
//! Return code: `0` on success, `1` on error. On error, `out_err` holds
//! a JSON error envelope rendered by `ktav::ErrorEnvelope::to_json()`
//! (nine fields, all present, `null` when absent) and must still be
//! freed via `ktav_free`.

use std::os::raw::{c_char, c_int};
use std::ptr;
use std::slice;

use indexmap::IndexMap;
use ktav::value::{ObjectMap, Value};
use serde::de::{self, MapAccess, Visitor};
use serde::{Deserialize, Deserializer};
use serde_json::{Map as JsonMap, Value as Json};

/// Written into the caller's `**u8` / `*usize` on success.
#[inline]
unsafe fn emit(buf: Vec<u8>, out_buf: *mut *mut u8, out_len: *mut usize) {
    let mut boxed = buf.into_boxed_slice();
    let len = boxed.len();
    let ptr = boxed.as_mut_ptr();
    std::mem::forget(boxed);
    *out_buf = ptr;
    *out_len = len;
}

unsafe fn emit_err(msg: String, out_err: *mut *mut c_char, out_err_len: *mut usize) {
    let bytes = msg.into_bytes();
    let mut boxed = bytes.into_boxed_slice();
    let len = boxed.len();
    let ptr = boxed.as_mut_ptr() as *mut c_char;
    std::mem::forget(boxed);
    *out_err = ptr;
    *out_err_len = len;
}

/// Write `err` as a JSON envelope against `source` and return the ABI error code.
unsafe fn emit_err_envelope(
    err: &ktav::Error,
    source: &str,
    out_err: *mut *mut c_char,
    out_err_len: *mut usize,
) -> c_int {
    emit_err(
        ktav::ErrorEnvelope::from_error(err, source).to_json(),
        out_err,
        out_err_len,
    );
    1
}

/// Parse a Ktav document. Returns JSON bytes on success, JSON error envelope
/// on failure. Caller frees both via `ktav_free`.
///
/// # Safety
/// `src` must point to `src_len` valid bytes. Output pointers must be
/// valid for writes.
#[no_mangle]
pub unsafe extern "C" fn ktav_loads(
    src: *const u8,
    src_len: usize,
    out_buf: *mut *mut u8,
    out_len: *mut usize,
    out_err: *mut *mut c_char,
    out_err_len: *mut usize,
) -> c_int {
    *out_buf = ptr::null_mut();
    *out_len = 0;
    *out_err = ptr::null_mut();
    *out_err_len = 0;

    let input = match std::str::from_utf8(slice::from_raw_parts(src, src_len)) {
        Ok(s) => s,
        Err(e) => {
            let err = ktav::Error::Message(format!("input is not valid UTF-8: {e}"));
            return emit_err_envelope(&err, "", out_err, out_err_len);
        }
    };

    let value = match ktav::parse(input) {
        Ok(v) => v,
        Err(e) => return emit_err_envelope(&e, input, out_err, out_err_len),
    };

    let json = value_to_json(&value);
    let bytes = match serde_json::to_vec(&json) {
        Ok(b) => b,
        Err(e) => {
            let err = ktav::Error::Message(format!("internal: encode JSON: {e}"));
            return emit_err_envelope(&err, "", out_err, out_err_len);
        }
    };

    emit(bytes, out_buf, out_len);
    0
}

/// Parse a Ktav document with strict numeric spelling checks. Returns JSON
/// bytes on success, JSON error envelope on failure. Caller frees both via
/// `ktav_free`.
///
/// # Safety
/// Same as [`ktav_loads`].
#[no_mangle]
pub unsafe extern "C" fn ktav_loads_strict(
    src: *const u8,
    src_len: usize,
    out_buf: *mut *mut u8,
    out_len: *mut usize,
    out_err: *mut *mut c_char,
    out_err_len: *mut usize,
) -> c_int {
    *out_buf = ptr::null_mut();
    *out_len = 0;
    *out_err = ptr::null_mut();
    *out_err_len = 0;

    let input = match std::str::from_utf8(slice::from_raw_parts(src, src_len)) {
        Ok(s) => s,
        Err(e) => {
            let err = ktav::Error::Message(format!("input is not valid UTF-8: {e}"));
            return emit_err_envelope(&err, "", out_err, out_err_len);
        }
    };

    let value = match ktav::parse_strict(input) {
        Ok(v) => v,
        Err(e) => return emit_err_envelope(&e, input, out_err, out_err_len),
    };

    let json = value_to_json(&value);
    let bytes = match serde_json::to_vec(&json) {
        Ok(b) => b,
        Err(e) => {
            let err = ktav::Error::Message(format!("internal: encode JSON: {e}"));
            return emit_err_envelope(&err, "", out_err, out_err_len);
        }
    };

    emit(bytes, out_buf, out_len);
    0
}

/// Render a JSON document (as produced by `ktav_loads` or built by the
/// caller to the same schema) to Ktav text.
///
/// # Safety
/// Same as [`ktav_loads`].
#[no_mangle]
pub unsafe extern "C" fn ktav_dumps(
    src: *const u8,
    src_len: usize,
    out_buf: *mut *mut u8,
    out_len: *mut usize,
    out_err: *mut *mut c_char,
    out_err_len: *mut usize,
) -> c_int {
    *out_buf = ptr::null_mut();
    *out_len = 0;
    *out_err = ptr::null_mut();
    *out_err_len = 0;

    let bytes = slice::from_raw_parts(src, src_len);
    let wire: WireValue = match serde_json::from_slice(bytes) {
        Ok(w) => w,
        Err(e) => {
            let err = ktav::Error::Message(format!("input JSON: {e}"));
            return emit_err_envelope(&err, "", out_err, out_err_len);
        }
    };

    let value = match wire.into_value() {
        Ok(v) => v,
        Err(e) => {
            let err = ktav::Error::Message(e);
            return emit_err_envelope(&err, "", out_err, out_err_len);
        }
    };

    if !matches!(value, Value::Object(_) | Value::Array(_)) {
        let err =
            ktav::Error::Message("top-level Ktav document must be an object or array".to_string());
        return emit_err_envelope(&err, "", out_err, out_err_len);
    }

    let text = match ktav::render::render(&value) {
        Ok(s) => s,
        Err(e) => return emit_err_envelope(&e, "", out_err, out_err_len),
    };

    emit(text.into_bytes(), out_buf, out_len);
    0
}

/// Render a JSON document to Ktav text, coercing every leaf scalar to a
/// String (typed integers, typed floats, booleans, and `null` are
/// flattened to their textual form via the `::` raw marker). Compounds
/// preserve their structure. See `ktav::to_string_force_strings`.
///
/// # Safety
/// Same as [`ktav_loads`].
#[no_mangle]
pub unsafe extern "C" fn ktav_dumps_force_strings(
    src: *const u8,
    src_len: usize,
    out_buf: *mut *mut u8,
    out_len: *mut usize,
    out_err: *mut *mut c_char,
    out_err_len: *mut usize,
) -> c_int {
    *out_buf = ptr::null_mut();
    *out_len = 0;
    *out_err = ptr::null_mut();
    *out_err_len = 0;

    let bytes = slice::from_raw_parts(src, src_len);
    let wire: WireValue = match serde_json::from_slice(bytes) {
        Ok(w) => w,
        Err(e) => {
            let err = ktav::Error::Message(format!("input JSON: {e}"));
            return emit_err_envelope(&err, "", out_err, out_err_len);
        }
    };

    let value = match wire.into_value() {
        Ok(v) => v,
        Err(e) => {
            let err = ktav::Error::Message(e);
            return emit_err_envelope(&err, "", out_err, out_err_len);
        }
    };

    if !matches!(value, Value::Object(_) | Value::Array(_)) {
        let err =
            ktav::Error::Message("top-level Ktav document must be an object or array".to_string());
        return emit_err_envelope(&err, "", out_err, out_err_len);
    }

    let text = match ktav::to_string_force_strings(&value) {
        Ok(s) => s,
        Err(e) => return emit_err_envelope(&e, "", out_err, out_err_len),
    };

    emit(text.into_bytes(), out_buf, out_len);
    0
}

/// Render a JSON document (as produced by `ktav_loads`) to the canonical
/// Ktav text form (spec § 7). Output is deterministic and round-trips
/// through `ktav_loads` / `ktav_dumps` unchanged.
///
/// # Safety
/// Same as [`ktav_loads`].
#[no_mangle]
pub unsafe extern "C" fn ktav_emit_canonical(
    src: *const u8,
    src_len: usize,
    out_buf: *mut *mut u8,
    out_len: *mut usize,
    out_err: *mut *mut c_char,
    out_err_len: *mut usize,
) -> c_int {
    *out_buf = ptr::null_mut();
    *out_len = 0;
    *out_err = ptr::null_mut();
    *out_err_len = 0;

    let bytes = slice::from_raw_parts(src, src_len);
    let wire: WireValue = match serde_json::from_slice(bytes) {
        Ok(w) => w,
        Err(e) => {
            let err = ktav::Error::Message(format!("input JSON: {e}"));
            return emit_err_envelope(&err, "", out_err, out_err_len);
        }
    };

    let value = match wire.into_value() {
        Ok(v) => v,
        Err(e) => {
            let err = ktav::Error::Message(e);
            return emit_err_envelope(&err, "", out_err, out_err_len);
        }
    };

    if !matches!(value, Value::Object(_) | Value::Array(_)) {
        let err =
            ktav::Error::Message("top-level Ktav document must be an object or array".to_string());
        return emit_err_envelope(&err, "", out_err, out_err_len);
    }

    let text = match ktav::emit_canonical(&value) {
        Ok(s) => s,
        Err(e) => return emit_err_envelope(&e, "", out_err, out_err_len),
    };

    emit(text.into_bytes(), out_buf, out_len);
    0
}

/// Format Ktav source text. Input is Ktav source text (not JSON); every
/// comment is preserved verbatim (spec § 3.4: a comment owns a whole line,
/// so attachment is unambiguous). Blank lines survive as a grouping hint,
/// but a run of two or more collapses to exactly one and blank padding
/// immediately inside a bracket is dropped — this makes the transform a
/// fixed point. Key order is never changed (canonical form has no sorting
/// rule, § 5.9). For a document with no comments AND no blank lines the
/// result equals `ktav_emit_canonical` of its parse (the stronger condition
/// is deliberate — blank lines are no more part of the Value model than
/// comments). Returns formatted text on success, JSON error envelope on
/// failure. Caller frees both via `ktav_free`.
///
/// # Safety
/// Same as [`ktav_loads`].
#[no_mangle]
pub unsafe extern "C" fn ktav_format(
    src: *const u8,
    src_len: usize,
    out_buf: *mut *mut u8,
    out_len: *mut usize,
    out_err: *mut *mut c_char,
    out_err_len: *mut usize,
) -> c_int {
    *out_buf = ptr::null_mut();
    *out_len = 0;
    *out_err = ptr::null_mut();
    *out_err_len = 0;

    let text = match std::str::from_utf8(slice::from_raw_parts(src, src_len)) {
        Ok(s) => s,
        Err(e) => {
            let err = ktav::Error::Message(format!("input is not valid UTF-8: {e}"));
            return emit_err_envelope(&err, "", out_err, out_err_len);
        }
    };

    match ktav::format_str(text) {
        Ok(formatted) => {
            emit(formatted.into_bytes(), out_buf, out_len);
            0
        }
        Err(e) => emit_err_envelope(&e, text, out_err, out_err_len),
    }
}

/// Free a buffer returned by any ABI function, including `ktav_format`
/// (success or error). `ptr`/`len` is a no-op when null/zero.
///
/// # Safety
/// Must be called exactly once per returned buffer with the same length
/// it was returned with.
#[no_mangle]
pub unsafe extern "C" fn ktav_free(ptr: *mut u8, len: usize) {
    if ptr.is_null() || len == 0 {
        return;
    }
    let _ = Box::from_raw(std::ptr::slice_from_raw_parts_mut(ptr, len));
}

/// NUL-terminated static version string (crate version). For sanity
/// checks from the Go side that `LoadLibrary` picked up the right file.
#[no_mangle]
pub extern "C" fn ktav_version() -> *const c_char {
    concat!(env!("CARGO_PKG_VERSION"), "\0").as_ptr() as *const c_char
}

// ─── Value ↔ JSON conversion ──────────────────────────────────────────────

fn value_to_json(v: &Value) -> Json {
    match v {
        Value::Null => Json::Null,
        Value::Bool(b) => Json::Bool(*b),
        Value::Integer(s) => {
            let mut m = JsonMap::new();
            m.insert("$i".to_string(), Json::String(s.to_string()));
            Json::Object(m)
        }
        Value::Float(s) => {
            let mut m = JsonMap::new();
            m.insert("$f".to_string(), Json::String(s.to_string()));
            Json::Object(m)
        }
        Value::String(s) => Json::String(s.to_string()),
        Value::Array(a) => Json::Array(a.iter().map(value_to_json).collect()),
        Value::Object(o) => {
            let mut m = JsonMap::new();
            for (k, val) in o {
                m.insert(k.to_string(), value_to_json(val));
            }
            Json::Object(m)
        }
    }
}

/// Deserialize target that understands both plain JSON values and the
/// `{"$i": ...}` / `{"$f": ...}` tagged wrappers, preserving object key
/// order via `indexmap`.
enum WireValue {
    Null,
    Bool(bool),
    Integer(String),
    Float(String),
    String(String),
    Array(Vec<WireValue>),
    Object(IndexMap<String, WireValue>),
}

impl WireValue {
    fn into_value(self) -> Result<Value, String> {
        match self {
            WireValue::Null => Ok(Value::Null),
            WireValue::Bool(b) => Ok(Value::Bool(b)),
            WireValue::Integer(s) => {
                validate_integer(&s)?;
                Ok(Value::Integer(s.into()))
            }
            WireValue::Float(s) => {
                validate_float(&s)?;
                Ok(Value::Float(s.into()))
            }
            WireValue::String(s) => Ok(Value::String(s.into())),
            WireValue::Array(items) => {
                let mut out = Vec::with_capacity(items.len());
                for w in items {
                    out.push(w.into_value()?);
                }
                Ok(Value::Array(out))
            }
            WireValue::Object(m) => {
                let mut obj = ObjectMap::with_capacity_and_hasher(m.len(), Default::default());
                for (k, v) in m {
                    obj.insert(k.into(), v.into_value()?);
                }
                Ok(Value::Object(obj))
            }
        }
    }
}

fn validate_integer(s: &str) -> Result<(), String> {
    let rest = s.strip_prefix('-').unwrap_or(s);
    if rest.is_empty() || !rest.bytes().all(|b| b.is_ascii_digit()) {
        return Err(format!("$i payload not an integer literal: {s:?}"));
    }
    Ok(())
}

fn validate_float(s: &str) -> Result<(), String> {
    if s.parse::<f64>().is_err() {
        return Err(format!("$f payload not a finite decimal: {s:?}"));
    }
    if !s.bytes().any(|b| b == b'.' || b == b'e' || b == b'E') {
        return Err(format!("$f payload must contain '.' or exponent: {s:?}"));
    }
    Ok(())
}

impl<'de> Deserialize<'de> for WireValue {
    fn deserialize<D: Deserializer<'de>>(d: D) -> Result<Self, D::Error> {
        struct V;
        impl<'de> Visitor<'de> for V {
            type Value = WireValue;
            fn expecting(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
                f.write_str("a JSON value")
            }
            fn visit_unit<E: de::Error>(self) -> Result<WireValue, E> {
                Ok(WireValue::Null)
            }
            fn visit_none<E: de::Error>(self) -> Result<WireValue, E> {
                Ok(WireValue::Null)
            }
            fn visit_some<D: Deserializer<'de>>(self, d: D) -> Result<WireValue, D::Error> {
                WireValue::deserialize(d)
            }
            fn visit_bool<E: de::Error>(self, b: bool) -> Result<WireValue, E> {
                Ok(WireValue::Bool(b))
            }
            fn visit_i64<E: de::Error>(self, n: i64) -> Result<WireValue, E> {
                Ok(WireValue::Integer(n.to_string()))
            }
            fn visit_u64<E: de::Error>(self, n: u64) -> Result<WireValue, E> {
                Ok(WireValue::Integer(n.to_string()))
            }
            fn visit_f64<E: de::Error>(self, n: f64) -> Result<WireValue, E> {
                if !n.is_finite() {
                    return Err(E::custom("NaN / ±Infinity not allowed in Ktav"));
                }
                // Bare JSON floats get the ":f" wire form with a forced
                // decimal point so render's grammar check is satisfied.
                let mut s = format!("{n}");
                if !s.contains('.') && !s.contains('e') && !s.contains('E') {
                    s.push_str(".0");
                }
                Ok(WireValue::Float(s))
            }
            fn visit_str<E: de::Error>(self, v: &str) -> Result<WireValue, E> {
                Ok(WireValue::String(v.to_string()))
            }
            fn visit_string<E: de::Error>(self, v: String) -> Result<WireValue, E> {
                Ok(WireValue::String(v))
            }
            fn visit_seq<A: de::SeqAccess<'de>>(self, mut seq: A) -> Result<WireValue, A::Error> {
                let mut out = Vec::new();
                while let Some(item) = seq.next_element()? {
                    out.push(item);
                }
                Ok(WireValue::Array(out))
            }
            fn visit_map<A: MapAccess<'de>>(self, mut map: A) -> Result<WireValue, A::Error> {
                let Some(k1) = map.next_key::<String>()? else {
                    return Ok(WireValue::Object(IndexMap::new()));
                };
                let v1: WireValue = map.next_value()?;
                let second_key: Option<String> = map.next_key()?;

                if second_key.is_none() && (k1 == "$i" || k1 == "$f") {
                    let payload = match v1 {
                        WireValue::String(s) => s,
                        WireValue::Integer(s) => s,
                        WireValue::Float(s) => s,
                        _ => {
                            return Err(de::Error::custom(format!("{k1} payload must be a string")))
                        }
                    };
                    return Ok(if k1 == "$i" {
                        WireValue::Integer(payload)
                    } else {
                        WireValue::Float(payload)
                    });
                }

                let mut out: IndexMap<String, WireValue> = IndexMap::new();
                out.insert(k1, v1);
                if let Some(k2) = second_key {
                    let v2: WireValue = map.next_value()?;
                    out.insert(k2, v2);
                    while let Some((k, v)) = map.next_entry::<String, WireValue>()? {
                        out.insert(k, v);
                    }
                }
                Ok(WireValue::Object(out))
            }
        }

        d.deserialize_any(V)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    type AbiFn = unsafe extern "C" fn(
        *const u8,
        usize,
        *mut *mut u8,
        *mut usize,
        *mut *mut c_char,
        *mut usize,
    ) -> c_int;

    /// Call a six-parameter ABI entry point with `input`, returning
    /// (rc, out bytes, error payload). All returned buffers are freed
    /// through `ktav_free`.
    unsafe fn call_abi(f: AbiFn, input: &[u8]) -> (c_int, Option<Vec<u8>>, Option<String>) {
        let mut out_buf: *mut u8 = ptr::null_mut();
        let mut out_len: usize = 0;
        let mut err_buf: *mut c_char = ptr::null_mut();
        let mut err_len: usize = 0;
        let rc = f(
            input.as_ptr(),
            input.len(),
            &mut out_buf,
            &mut out_len,
            &mut err_buf,
            &mut err_len,
        );
        let out = if out_buf.is_null() {
            assert_eq!(out_len, 0);
            None
        } else {
            Some(Vec::from(slice::from_raw_parts(out_buf, out_len)))
        };
        let err = if err_buf.is_null() {
            assert_eq!(err_len, 0);
            None
        } else {
            Some(
                String::from_utf8(Vec::from(slice::from_raw_parts(
                    err_buf as *const u8,
                    err_len,
                )))
                .expect("error payload is UTF-8"),
            )
        };
        ktav_free(out_buf, out_len);
        ktav_free(err_buf as *mut u8, err_len);
        (rc, out, err)
    }

    #[test]
    fn format_preserves_comments_and_is_fixed_point() {
        unsafe {
            let src = b"# header comment\n\n\n\na: 1  # inner comment\n\nb: 2\n";
            let (rc1, out1, err1) = call_abi(ktav_format, src);
            assert_eq!(rc1, 0, "err: {err1:?}");
            let bytes1 = out1.expect("output");
            let once = std::str::from_utf8(&bytes1).unwrap();
            for comment in ["# header comment", "# inner comment"] {
                assert!(
                    once.lines().any(|l| l.contains(comment)),
                    "comment {comment:?} missing from:\n{once}"
                );
            }
            let (rc2, out2, err2) = call_abi(ktav_format, once.as_bytes());
            assert_eq!(rc2, 0, "err: {err2:?}");
            let bytes2 = out2.expect("output");
            let twice = std::str::from_utf8(&bytes2).unwrap();
            assert_eq!(once, twice, "format is not a fixed point");
        }
    }

    #[test]
    fn format_equals_emit_canonical_without_trivia() {
        unsafe {
            let doc = b"width: 800\nheight: 600\ntags: [\"a\", \"b\"]\nmeta: {ok: true}\n";
            let (rc, out, err) = call_abi(ktav_format, doc);
            assert_eq!(rc, 0, "err: {err:?}");
            let formatted = out.expect("format output");

            let (rc, json, err) = call_abi(ktav_loads, doc);
            assert_eq!(rc, 0, "err: {err:?}");
            let json = json.expect("loads output");
            let (rc, canonical, err) = call_abi(ktav_emit_canonical, &json);
            assert_eq!(rc, 0, "err: {err:?}");
            let canonical = canonical.expect("emit_canonical output");

            assert_eq!(
                formatted, canonical,
                "format != canonical for trivia-free doc"
            );
        }
    }

    #[test]
    fn format_invalid_utf8_is_envelope_error() {
        unsafe {
            let (rc, out, err) = call_abi(ktav_format, &[0xFF, 0xFE, b'a']);
            assert_eq!(rc, 1);
            assert!(out.is_none(), "out_buf should stay null");
            let payload = err.expect("error payload");
            let v: Json = serde_json::from_str(&payload).expect("payload is JSON");
            let obj = v.as_object().expect("envelope is an object");
            for key in [
                "error",
                "reason",
                "line",
                "line_text",
                "span",
                "path",
                "body",
                "canonical",
                "spec_section",
            ] {
                assert!(obj.contains_key(key), "missing key {key:?} in {payload}");
            }
            assert_eq!(obj["error"], "Message");
        }
    }

    #[test]
    fn format_syntax_error_surfaces_envelope_fields() {
        unsafe {
            let (rc, out, err) = call_abi(ktav_format, b"a: [");
            assert_eq!(rc, 1);
            assert!(out.is_none());
            let payload = err.expect("error payload");
            let v: Json = serde_json::from_str(&payload).expect("payload is JSON");
            let obj = v.as_object().expect("envelope is an object");
            let error = obj["error"].as_str().expect("error is a string");
            assert!(!error.is_empty());
            // Observed against ktav 0.7.1: this parse error is
            // `UnclosedCompound` (spec §6.1) with a populated byte-offset
            // `span`; `line` is null because `Error::line()` is only
            // populated for structured kinds carrying line info.
            assert_eq!(obj["error"], "UnclosedCompound");
            assert!(
                obj["span"].is_object(),
                "span must be populated for parse-time errors: {payload}"
            );
            assert!(
                obj["reason"].is_null(),
                "parse-time errors carry no reason code: {payload}"
            );
        }
    }
}
