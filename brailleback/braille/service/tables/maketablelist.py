#!/usr/bin/env python3
"""Generate res/xml/tablelist.xml from the vendored liblouis table set.

Reads the `#+language:`, `#+region:`, `#+type:`, `#+dots:`, `#+grade:`,
`#+system:` and `#+variant:` metadata comments that modern liblouis tables
carry in their headers.  Tables without metadata are still included using a
filename-based heuristic so that no language is silently dropped.

Two kinds of tables are filtered out:

* special-purpose tables (chess, math, IPA, ...) that are not user facing;
* pure include aliases (a file whose only rule line is `include X`), because
  they are the exact same table as X and would otherwise appear twice in the
  list (e.g. ar.tbl is `include ar-ar-g1.utb`).

Variants are taken from `#+variant:`/`#+system:` metadata or, failing that,
derived from the `#-index-name:` display metadata (e.g. "Akkadian, Borger"
yields the variant "Borger").

Usage: python3 maketablelist.py
Writes: ../res/xml/tablelist.xml
Prints:  a summary of the generated tables plus the ids to paste into the
         app's braille_tables array and the default-table preferences.
"""

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
TABLES_DIR = os.path.normpath(os.path.join(
    HERE, '..', 'jni', 'liblouiswrapper', 'liblouis', 'tables'))
OUT_XML = os.path.normpath(os.path.join(HERE, '..', 'res', 'xml', 'tablelist.xml'))

EXTENSIONS = ('.ctb', '.utb', '.tbl')

# Filename tokens that describe the braille type/grade rather than a region.
KEYWORD_TOKENS = frozenset([
    'g0', 'g1', 'g2', 'g3', 'comp', 'comp6', 'comp8', 'compbrl',
    '6dot', '8dot', 'brf', 'math', 'chess', 'interline', 'edit',
    'core', 'detailed', 'misc', 'chars', 'accents', 'octobraille',
    'system', 'lit', 'text', 'table', 'lowered', 'litdigits',
])

META_RE = re.compile(r'^#\+([a-z]+)\s*:\s*(.*?)\s*$')
INDEX_NAME_RE = re.compile(r'^#-index-name\s*:\s*(.*?)\s*$')

# Special-purpose tables (chess, math, transcription codes, ...) that must not
# appear in the user-facing table list.  They are matched against the file
# name because most of them carry no language metadata.
SPECIAL_FILE_TOKENS = (
    'chess', 'math', 'mathtext', 'interline', 'nabcc', 'ipa',
    'unicode', 'uni-', 'spaces', 'misc', 'edit', 'brl', 'brf',
)

# Tokens inside an index-name that only restate the type/grade/dots already
# captured in the table metadata, and therefore add nothing to a variant.
REDUNDANT_INDEX_TOKENS = (
    'uncontracted', 'contracted', 'partially', 'computer', 'literary',
    '6-dot', '8-dot', 'six-dot', 'eight-dot', 'grade', 'g1', 'g2', 'g3',
    '1', '2', '3',
)


def parse_metadata(path):
    meta = {}
    index_name = ''
    try:
        with open(path, encoding='utf-8', errors='replace') as f:
            for line in f:
                m = META_RE.match(line)
                if m:
                    meta.setdefault(m.group(1), m.group(2))
                    continue
                m = INDEX_NAME_RE.match(line)
                if m and not index_name:
                    index_name = m.group(1)
    except OSError:
        pass
    meta['index-name'] = index_name
    return meta


def is_pure_include(path):
    """True when the file's only rule line is `include X`, i.e. the table is
    an exact alias of another table."""
    meaningful = []
    try:
        with open(path, encoding='utf-8', errors='replace') as f:
            for line in f:
                line = line.strip()
                if not line or line.startswith('#'):
                    continue
                meaningful.append(line)
    except OSError:
        return False
    return len(meaningful) == 1 and meaningful[0].startswith('include')


def parse_region(value):
    """Normalize a #+region: value into an ISO-ish region code or ''."""
    if not value:
        return ''
    value = value.strip().lstrip('*-')
    # Values may look like 'en-US', 'cmn-CN', 'en', '*-IL'.
    parts = [p for p in re.split(r'[-\s]', value) if p]
    if not parts:
        return ''
    # Prefer the last token that looks like an uppercase region (2-4 letters).
    for p in reversed(parts):
        if re.fullmatch(r'[A-Za-z]{2,4}', p) and p.lower() not in ('cmn', 'yue'):
            return p.upper()
    return ''


def lang_from_filename(basename):
    """Heuristic fallback for tables without metadata."""
    stem = basename[: -4]
    parts = stem.split('-')
    lang = parts[0].lower()
    region = ''
    grade = 1
    comp_type = ''
    for p in parts[1:]:
        pl = p.lower()
        if pl in KEYWORD_TOKENS or (pl.startswith('g') and pl[1:].isdigit()):
            if pl in ('comp', 'comp6', 'comp8', 'compbrl'):
                if not comp_type:
                    comp_type = pl
            elif pl.startswith('g') and pl[1:].isdigit():
                grade = int(pl[1:])
            continue
        if not region and re.fullmatch(r'[A-Za-z0-9]{2,4}', p) and p.lower() != lang:
            region = p
    return lang, region.upper(), grade, comp_type


def variant_from_index_name(index_name):
    """Derive a short distinguishing variant from the liblouis index-name,
    e.g. \"Danish, uncontracted, 6-dot, 2022\" -> \"2022\" and
    \"Akkadian, Borger\" -> \"Borger\"."""
    if not index_name:
        return ''
    parts = index_name.split(',', 1)
    if len(parts) < 2:
        return ''
    tail = parts[1]
    # Drop tokens that restate type/grade/dots already captured elsewhere.
    for token in REDUNDANT_INDEX_TOKENS:
        tail = re.sub(r'(?i)\b%s\b' % re.escape(token), ' ', tail)
    tail = re.sub(r'[,\-]+', ' ', tail)
    tail = re.sub(r'\s+', ' ', tail).strip()
    # Keep variants short and tidy.
    if len(tail) > 24:
        tail = tail[:24].strip()
    return tail


def pick(meta, key):
    val = meta.get(key)
    return val.strip() if val else ''


def is_special(name):
    low = name.lower()
    return any(tok in low for tok in SPECIAL_FILE_TOKENS)


def is_exact_name(lang, region, computer, grade, name):
    """True when the file name is exactly lang[-region]-TYPE (e.g.
    en-US-comp8.ctb) with no extra tokens.  Used to give the plain table id to
    the canonical table when several files collide."""
    stem = name[:-4].lower()
    prefix = lang.lower() + ('-' + region.lower() if region else '')
    if not stem.startswith(prefix):
        return False
    rest = stem[len(prefix):].lstrip('-')
    return rest in ('', 'g0', 'g1', 'g2', 'g3', 'comp', 'comp6', 'comp8', 'compbrl')


def main():
    entries = []
    skipped_aliases = []
    for name in sorted(os.listdir(TABLES_DIR)):
        if not name.endswith(EXTENSIONS):
            continue
        if is_special(name):
            continue
        path = os.path.join(TABLES_DIR, name)
        if is_pure_include(path):
            skipped_aliases.append(name)
            continue
        meta = parse_metadata(path)
        language = pick(meta, 'language')
        comp_type = ''
        if not language:
            lang, region, grade, comp_type = lang_from_filename(name)
            computer = bool(comp_type)
            dots = 6 if comp_type == 'comp6' else 8
        else:
            lang = language.split(',')[0].strip().lower()
            region = parse_region(pick(meta, 'region'))
            ttype = pick(meta, 'type').lower().replace(' ', '')
            computer = ttype == 'computer'
            dots_meta = pick(meta, 'dots')
            grade_meta = pick(meta, 'grade')
            try:
                dots = int(dots_meta)
            except (TypeError, ValueError):
                dots = 8 if computer else 6
            try:
                grade = int(grade_meta)
            except (TypeError, ValueError):
                grade = 1
            if dots == 8:
                computer = True
                comp_type = comp_type or 'comp8'
            elif computer:
                comp_type = 'comp6' if dots == 6 else 'comp8'
            else:
                comp_type = ''
        if not lang or not re.fullmatch(r'[a-z]{2,3}', lang):
            continue

        if computer:
            grade = None
        else:
            dots = 6
            if grade is None:
                grade = 1

        base = lang
        if region:
            base = '%s-%s' % (lang, region)
        if computer:
            table_id = '%s-%s' % (base, comp_type or 'comp8')
        else:
            table_id = '%s-g%s' % (base, grade)

        system = pick(meta, 'system')
        variant = pick(meta, 'variant') or system
        entries.append({
            'id': table_id,
            'base': base,
            'lang': lang,
            'region': region,
            'dots': dots,
            'grade': grade,
            'variant': variant,
            'fileName': name,
            'system': system,
            'index_name': meta.get('index-name', ''),
            'exact': is_exact_name(lang, region, computer, grade, name),
        })

    # A #+system: value shared by *every* table of a language (e.g. Danish's
    # "ddp") does not distinguish anything, so prefer a variant derived from
    # the index-name (which usually carries the year or system name) instead.
    from collections import Counter
    system_counts = {}
    for e in entries:
        counts = system_counts.setdefault(e['lang'], Counter())
        counts[e['system'] or ''] += 1
        counts['_total'] += 1
    for e in entries:
        counts = system_counts[e['lang']]
        if (e['system']
                and counts[e['system']] == counts['_total']
                and counts['_total'] > 1):
            e['variant'] = ''
        if not e['variant']:
            e['variant'] = variant_from_index_name(e['index_name'])

    # Give the plain id to the canonical (exact-named) table first.
    entries.sort(key=lambda e: (0 if e['exact'] else 1, e['fileName']))

    # Disambiguate duplicate ids (same locale+grade/dots from several files).
    counters = {}
    for e in entries:
        base_id = e['id']
        counters[base_id] = counters.get(base_id, 0) + 1
        n = counters[base_id]
        if n == 1:
            e['id'] = base_id
            continue
        sys_suffix = re.sub(r'[^a-zA-Z0-9]', '', e.get('system') or '').lower()
        if sys_suffix and '%s-%s' % (base_id, sys_suffix) not in counters:
            e['id'] = '%s-%s' % (base_id, sys_suffix)
            counters[e['id']] = 1
        else:
            e['id'] = '%s-%d' % (base_id, n)

    entries.sort(key=lambda e: (e['lang'], e['region'], e['dots'], e['grade'] or 0))

    with open(OUT_XML, 'w', encoding='utf-8', newline='\n') as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n')
        f.write('\n<!-- Generated by maketablelist.py from the vendored liblouis\n')
        f.write('     table set.  Do not edit by hand. -->\n\n')
        f.write('<table-list>\n\n')
        for e in entries:
            attrs = ['id="%s"' % e['id'],
                     'locale="%s"' % ('%s_%s' % (e['lang'], e['region']) if e['region'] else e['lang']),
                     'dots="%d"' % e['dots']]
            if e['grade'] is not None:
                attrs.append('grade="%d"' % e['grade'])
            if e['variant']:
                attrs.append('variant="%s"' % e['variant'])
            attrs.append('fileName="%s"' % e['fileName'])
            f.write('    <table\n        %s />\n' % ('\n        '.join(attrs)))
        f.write('\n</table-list>\n')

    literary = [e for e in entries if e['dots'] == 6]
    computer = [e for e in entries if e['dots'] == 8]
    print('Generated %s entries (%d literary, %d computer) -> %s' % (
        len(entries), len(literary), len(computer), OUT_XML))
    if skipped_aliases:
        print('Skipped %d include-alias tables: %s' % (
            len(skipped_aliases), ', '.join(sorted(skipped_aliases))))

    ids = [e['id'] for e in entries]
    print('\nBraille tables array items (%d):' % len(ids))
    for i in ids:
        print('        <item>%s</item>' % i)

    def best(pred):
        for e in entries:
            if pred(e):
                return e['id']
        return None

    lit_default = best(lambda e: e['fileName'] == 'en-ueb-g1.ctb') or \
        best(lambda e: e['lang'] == 'en' and e['dots'] == 6 and e['grade'] == 1)
    comp_default = best(lambda e: e['fileName'] == 'en-us-comp8.ctb') or \
        best(lambda e: e['lang'] == 'en' and e['dots'] == 8)
    print('\nDefaults:')
    print('pref_braille_literary_table_default = %s' % lit_default)
    print('pref_braille_computer_table_default = %s' % comp_default)

    langs = sorted({e['lang'] for e in entries})
    print('\nLanguages covered (%d): %s' % (len(langs), ', '.join(langs)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
