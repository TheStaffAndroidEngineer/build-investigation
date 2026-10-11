#!/usr/bin/env python3
"""Retain separate final APK witnesses; these runs are not added to timed samples."""
from pathlib import Path
import importlib.util,json,zipfile,hashlib
path=Path(__file__).with_name('investigate-builds.py')
spec=importlib.util.spec_from_file_location('investigation',path)
driver=importlib.util.module_from_spec(spec);spec.loader.exec_module(driver)
suite=driver.Suite('message-confirmation')
args=[':app:assembleDebug','-Pboundary=narrow','--no-build-cache','--configuration-cache']
try:
    suite.run('confirmation-baseline-first',args)
    baseline=suite.run('confirmation-baseline-warm',args)
    driver.edit(*driver.EDITS['private-body'])
    edited=suite.run('confirmation-wording-edit',args)
    suite.restore()
    restored=suite.run('confirmation-restored-baseline',args)
    assert baseline['feature_classes']==edited['feature_classes']
    def contents(record):
        with zipfile.ZipFile(driver.ROOT/record['retained_apk']) as apk:
            return {name:hashlib.sha256(apk.read(name)).hexdigest() for name in apk.namelist()}
    assert contents(baseline)==contents(restored)
    assert baseline['source_sha256']==restored['source_sha256']
    (suite.out/'comparison.json').write_text(json.dumps({'scope':'Supplementary retained APKs, excluded from the24 measured edit samples','baseline':baseline['artifacts'],'wording':edited['artifacts'],'restored':restored['artifacts'],'feature_bytecode_unchanged':True,'restored_apk_entry_contents_equal':True,'apk_archive_byte_identity_required':False},indent=2)+'\n')
finally:suite.close()
print(suite.out)
