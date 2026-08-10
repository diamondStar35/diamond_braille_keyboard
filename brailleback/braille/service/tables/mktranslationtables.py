#!/usr/bin/env python3
"""Zip a liblouis tables directory into res/raw/translationtables.zip.

All files are stored under the 'liblouis/tables/' prefix, which matches the
path the native library searches below the configured data path.

Usage: python mktranslationtables.py <tables-dir> <output.zip>
"""

import os
import sys
import zipfile


def main():
    src = sys.argv[1]
    out = sys.argv[2]
    names = sorted(os.listdir(src))
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED) as zf:
        for name in names:
            full = os.path.join(src, name)
            if os.path.isfile(full):
                zf.write(full, os.path.join('liblouis', 'tables', name))
    print('Zipped %d table files into %s' % (len(names), out))
    return 0


if __name__ == '__main__':
    sys.exit(main())
