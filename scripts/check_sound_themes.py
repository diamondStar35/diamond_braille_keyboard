# Validate the sound theme assets: every config.ini must have an id, must map
# all known feedback events, and every referenced sound file must exist.
import io
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS = os.path.join(ROOT, 'assets', 'sounds')
EVENTS = ['type', 'type_upper', 'delete', 'new_line', 'open', 'close',
          'calibrate', 'misspelling', 'command']
errors = []

for folder in sorted(os.listdir(SOUNDS)):
    cfg = os.path.join(SOUNDS, folder, 'config.ini')
    if not os.path.isfile(cfg):
        errors.append('%s: missing config.ini' % folder)
        continue
    meta = {}
    sounds = {}
    section = ''
    for raw in io.open(cfg, encoding='utf-8'):
        line = raw.strip()
        if not line or line.startswith('#') or line.startswith(';'):
            continue
        if line.startswith('[') and line.endswith(']'):
            section = line[1:-1].lower()
            continue
        if '=' in line:
            key, _, value = line.partition('=')
            key, value = key.strip(), value.strip()
            (meta if section == 'meta' else sounds)[key.lower()] = value
    if 'id' not in meta:
        errors.append('%s: missing [meta] id' % folder)
    for event in EVENTS:
        if event not in sounds:
            errors.append('%s: missing sound mapping for %s' % (folder, event))
            continue
        value = sounds[event]
        if value not in ('system', 'none'):
            path = os.path.join(SOUNDS, folder, value)
            if not os.path.isfile(path):
                errors.append('%s: sound file missing: %s' % (folder, value))
    print('%s: id=%s sounds=%s' % (folder, meta.get('id'), sorted(sounds)))

if errors:
    print('ERRORS:')
    for e in errors:
        print('  - ' + e)
    raise SystemExit(1)
print('All theme configs OK')
