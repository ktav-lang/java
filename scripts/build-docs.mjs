#!/usr/bin/env node
// Rebuilds this repository's README/CHANGELOG/CONTRIBUTING/SECURITY from
// root-docs/, using @ktav-lang/polydoc. Run with --check for a CI-friendly,
// read-only verification instead of regenerating the files.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { configure, buildRootDocs } from '@ktav-lang/polydoc';

const LANGS = ['en', 'ru', 'zh'];
configure({
  langs: LANGS,
  rootDocuments: ['README', 'CHANGELOG', 'CONTRIBUTING', 'SECURITY'],
});

// polydoc's own writeRootDocs/checkRootDocs always write/read
// <repoRoot>/<DOC>.md and <repoRoot>/<DOC>.<lang>.md. This repository
// keeps CONTRIBUTING and SECURITY under docs/ and every translation
// under docs/<lang>/, so the output-path mapping below is local.
// Assembly and validation stay entirely in polydoc (buildRootDocs);
// only where the assembled buffers land is decided here.
const OUTPUT_PATHS = {
  README: {
    en: 'README.md',
    ru: 'docs/ru/README.ru.md',
    zh: 'docs/zh/README.zh.md',
  },
  CHANGELOG: {
    en: 'CHANGELOG.md',
    ru: 'docs/ru/CHANGELOG.ru.md',
    zh: 'docs/zh/CHANGELOG.zh.md',
  },
  CONTRIBUTING: {
    en: 'docs/CONTRIBUTING.md',
    ru: 'docs/ru/CONTRIBUTING.ru.md',
    zh: 'docs/zh/CONTRIBUTING.zh.md',
  },
  SECURITY: {
    en: 'docs/SECURITY.md',
    ru: 'docs/ru/SECURITY.ru.md',
    zh: 'docs/zh/SECURITY.zh.md',
  },
};

// Per-unit validation proves every meaning has every language. It does
// NOT prove the languages describe the same DOCUMENT: a heading demoted
// from ## to ### in one translation, or an extra heading in another,
// passes unit validation untouched. This check (from the reference
// repository's build script) catches that class of drift.
function headingSkeleton(markdown) {
  const levels = [];
  let fenceChar = null;
  let fenceLen = 0;
  for (const line of markdown.split('\n')) {
    const fence = line.match(/^\s{0,3}(`{3,}|~{3,})/u);
    if (fence) {
      const char = fence[1][0];
      const len = fence[1].length;
      if (fenceChar === null) { fenceChar = char; fenceLen = len; }
      else if (char === fenceChar && len >= fenceLen) { fenceChar = null; }
      continue;
    }
    if (fenceChar !== null) continue;
    const heading = line.match(/^(#{1,6})\s+\S/u);
    if (heading) levels.push(heading[1].length);
  }
  return levels;
}

function structuralProblems(label, perLang) {
  const [reference, ...others] = LANGS;
  const base = headingSkeleton(perLang.get(reference).toString('utf8'));
  const problems = [];
  for (const lang of others) {
    const other = headingSkeleton(perLang.get(lang).toString('utf8'));
    if (other.length !== base.length) {
      problems.push(`${label}: ${lang} has ${other.length} heading(s) but ${reference} has ` +
        `${base.length} — the translations describe different documents`);
      continue;
    }
    const at = base.findIndex((level, i) => level !== other[i]);
    if (at !== -1) {
      problems.push(`${label}: heading #${at + 1} is level ${other[at]} in ${lang} but ` +
        `level ${base[at]} in ${reference}`);
    }
  }
  return problems;
}

function usage() {
  process.stderr.write(
    'usage: node scripts/build-docs.mjs [--check]\n' +
    '  (no args)  regenerate README/CHANGELOG/CONTRIBUTING/SECURITY\n' +
    '             (.md plus docs/ru/*.ru.md and docs/zh/*.zh.md)\n' +
    '  --check    verify the generated files match root-docs/ without writing\n'
  );
}

function cli() {
  const scriptDir = path.dirname(fileURLToPath(import.meta.url));
  const root = path.resolve(scriptDir, '..');

  const args = process.argv.slice(2);
  if (args.includes('-h') || args.includes('--help')) { usage(); process.exit(0); }
  if (args.length > 1 || (args.length === 1 && args[0] !== '--check')) {
    usage();
    process.exit(1);
  }
  const checkMode = args[0] === '--check';

  let docs;
  try {
    docs = buildRootDocs(root);
    // polydoc silently skips a root document whose unit tree is missing
    // when none of ITS expected root-level artifacts exist; our artifacts
    // live under docs/, so an incomplete tree must be an error here.
    for (const name of Object.keys(OUTPUT_PATHS)) {
      if (!docs.has(name)) {
        throw new Error(`root-docs/${name}/ produced no document — the unit tree is missing or empty`);
      }
    }
    for (const [name, perLang] of docs) {
      const problems = structuralProblems(name, perLang);
      if (problems.length > 0) {
        throw new Error(problems.join('\n'));
      }
    }
  } catch (e) {
    process.stderr.write(`build-docs: ${e.message}\n`);
    process.exit(1);
  }

  if (!checkMode) {
    for (const [name, perLang] of docs) {
      for (const lang of LANGS) {
        const outPath = path.join(root, OUTPUT_PATHS[name][lang]);
        fs.mkdirSync(path.dirname(outPath), { recursive: true });
        fs.writeFileSync(outPath, perLang.get(lang));
      }
    }
    process.stdout.write(`build-docs: assembled ${docs.size} document(s) from root-docs/\n`);
    process.exit(0);
  }

  const problems = [];
  for (const [name, perLang] of docs) {
    for (const lang of LANGS) {
      const rel = OUTPUT_PATHS[name][lang];
      const outPath = path.join(root, rel);
      const expected = perLang.get(lang);
      let actual;
      try {
        actual = fs.readFileSync(outPath);
      } catch (e) {
        problems.push(`${rel} is missing or unreadable (${e.message}); it is generated from ` +
          `root-docs/${name}/`);
        continue;
      }
      if (actual.equals(expected)) continue;
      let off = 0;
      const min = Math.min(actual.length, expected.length);
      while (off < min && actual[off] === expected[off]) off++;
      const line = expected.subarray(0, off).toString('utf8').split('\n').length;
      problems.push(
        `${rel} differs from what root-docs/${name}/ generates, first at byte ${off} ` +
        `(line ${line}); edit the unit source, never the generated file`);
    }
  }
  if (problems.length > 0) {
    for (const problem of problems) {
      process.stderr.write(`build-docs --check: ${problem}\n`);
    }
    process.exit(1);
  }
  process.exit(0);
}

const isMain = process.argv[1] !== undefined &&
  import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;
if (isMain) cli();
