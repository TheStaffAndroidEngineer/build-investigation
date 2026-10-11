#!/usr/bin/env python3
"""Observe the current Common Goods C18 APK without changing build inputs."""
import argparse
import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

from common_goods_ui import CommonGoodsUi
from fresh_android_xml import read_ui_xml


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'staff.android.builds'
SCOPE = (
    'Current Common Goods debug APK; API36 emulator UI/correctness observations '
    'only. This driver does not build, edit source or measure build performance. '
    'Each XML/PNG pair is a retained viewport, not one simultaneous full screen.'
)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path,
                        default=ROOT / 'app/build/outputs/apk/debug/app-debug.apk')
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    apk = args.apk.resolve()
    if not apk.is_file():
        parser.error('Build the current debug APK first or supply --apk: ' + str(apk))
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    output = (args.output or ROOT / 'evidence/common-goods-host' / stamp).resolve()
    output.mkdir(parents=True, exist_ok=False)
    for name in ('host_common_goods.py', 'common_goods_ui.py', 'fresh_android_xml.py'):
        (output / name).write_bytes(Path(__file__).with_name(name).read_bytes())

    adb = Path(os.environ['ANDROID_HOME']) / 'platform-tools/adb'
    events = []
    digest = hashlib.sha256(apk.read_bytes()).hexdigest()
    retained_apk = output / 'tested-app-debug.apk'
    shutil.copy2(apk, retained_apk)
    assert hashlib.sha256(retained_apk.read_bytes()).hexdigest() == digest, 'APK changed during retention'
    result = {
        'status': 'RUNNING', 'scope': SCOPE, 'serial': args.serial,
        'source_apk': str(apk), 'retained_apk': retained_apk.name,
        'apk_sha256': digest, 'events': events,
    }

    def run(*arguments):
        return subprocess.run(
            [str(adb), '-s', args.serial, *arguments],
            capture_output=True, check=True,
        ).stdout

    def xml():
        return read_ui_xml(run, '/sdcard/chapter18-current-window.xml', {PACKAGE})

    ui = CommonGoodsUi(xml, run, output)

    def capture(name, *expected):
        frames = ui.observe(name)
        content = '\n'.join(value for _, value in frames)
        missing = [text for text in expected if text not in content]
        event = {
            'screen': name, 'viewports': [label for label, _ in frames],
            'expected': expected, 'missing': missing, 'apk_sha256': digest,
        }
        events.append(event)
        assert not missing, (name, missing)

    try:
        avd = run('emu', 'avd', 'name').decode().strip()
        assert 'StaffAndroid_API_36' in avd, avd
        result['avd'] = avd
        (output / 'device.txt').write_bytes(run('shell', 'getprop'))
        run('shell', 'am', 'force-stop', PACKAGE)
        run('install', '-r', str(retained_apk))
        installed_path = run('shell', 'pm', 'path', PACKAGE).decode().strip().removeprefix('package:')
        assert installed_path.startswith('/') and '\n' not in installed_path, installed_path
        installed_hash = run('shell', 'sha256sum', installed_path).decode().split()[0]
        result.update(installed_apk=installed_path, installed_sha256=installed_hash)
        assert installed_hash == digest, 'Installed APK differs from the retained executable'
        run('shell', 'pm', 'clear', PACKAGE)
        run('shell', 'am', 'start', '-n', PACKAGE + '/.MainActivity')
        time.sleep(1)
        capture('01-saved-seed', 'Common Goods', 'Tea in Cart: 3')
        ui.tap('Add 1 Tea to Cart')
        capture('02-accepted-four', 'Tea in Cart: 4', 'Added 1. Cart now 4.')
        ui.tap('Add 1 Tea to Cart')
        capture('03-refused-five', 'Tea in Cart: 4',
                'Not added: Maximum quantity reached. Cart remains 4.')
        ui.tap('Open Cart')
        capture('04-cart-still-four', 'Tea in Cart: 4', 'Accepted revision: 1')
        result['status'] = 'PASS'
    except Exception as error:
        result.update(status='FAIL', error=str(error))
        raise
    finally:
        (output / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
        print(output)


if __name__ == '__main__':
    main()
