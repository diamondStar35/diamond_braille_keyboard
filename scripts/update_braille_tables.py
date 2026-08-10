#!/usr/bin/env python3
"""Sync the keyboard app's table configuration with the generated
tablelist.xml from the braille service.

Updates:
  res/values/arrays.xml   - the braille_tables string-array
  res/values/strings.xml  - pref_braille_literary_table_default and
                            pref_braille_computer_table_default

Usage: python scripts/update_braille_tables.py
"""

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
TABLELIST = os.path.join(ROOT, 'brailleback', 'braille', 'service', 'res',
                         'xml', 'tablelist.xml')
ARRAYS_XML = os.path.join(ROOT, 'res', 'values', 'arrays.xml')
STRINGS_XML = os.path.join(ROOT, 'res', 'values', 'strings.xml')

TABLE_RE = re.compile(
    r'<table\b(?P<attrs>(?:[^>]*\n)*?[^>]*?)/>', re.MULTILINE)


def attr(xml_block, name):
    m = re.search(name + r'="([^"]*)"', xml_block)
    return m.group(1) if m else None


def main():
    with open(TABLELIST, encoding='utf-8') as f:
        content = f.read()

    ids = []
    for block in TABLE_RE.finditer(content):
        table_id = attr(block.group('attrs'), 'id')
        if table_id:
            ids.append(table_id)

    if not ids:
        print('No tables found in %s' % TABLELIST)
        return 1

    items = '\n'.join('        <item>%s</item>' % i for i in ids)
    new_array = ('    <string-array\n        name="braille_tables">\n%s\n'
                 '    </string-array>' % items)

    with open(ARRAYS_XML, encoding='utf-8') as f:
        arrays = f.read()
    arrays, n = re.subn(
        r'(?s)<string-array\s+name="braille_tables">.*?</string-array>',
        new_array.replace('\\', '\\\\'), arrays, count=1)
    if n != 1:
        print('Could not locate braille_tables array in %s' % ARRAYS_XML)
        return 1
    with open(ARRAYS_XML, 'w', encoding='utf-8', newline='\n') as f:
        f.write(arrays)
    print('Updated braille_tables array (%d tables) in %s' % (len(ids),
                                                              ARRAYS_XML))

    with open(STRINGS_XML, encoding='utf-8') as f:
        strings = f.read()

    def first(pred):
        for block in TABLE_RE.finditer(content):
            if pred(block.group('attrs')):
                return attr(block.group('attrs'), 'id')
        return None

    lit_default = first(lambda a: attr(a, 'fileName') == 'en-ueb-g1.ctb')
    if not lit_default:
        lit_default = first(lambda a: attr(a, 'fileName') == 'en-g1.ctb')
    comp_default = first(lambda a: attr(a, 'fileName') == 'en-us-comp8.ctb')

    if lit_default:
        strings, n1 = re.subn(
            r'(?m)^    <string name="pref_braille_literary_table_default">.*</string>$',
            '    <string name="pref_braille_literary_table_default">%s</string>'
            % lit_default, strings, count=1)
        print('Literary default: %s' % lit_default)
    if comp_default:
        strings, n2 = re.subn(
            r'(?m)^    <string name="pref_braille_computer_table_default">.*</string>$',
            '    <string name="pref_braille_computer_table_default">%s</string>'
            % comp_default, strings, count=1)
        print('Computer default: %s' % comp_default)

    with open(STRINGS_XML, 'w', encoding='utf-8', newline='\n') as f:
        f.write(strings)
    print('Updated defaults in %s' % STRINGS_XML)
    return 0


if __name__ == '__main__':
    sys.exit(main())
