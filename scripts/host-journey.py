#!/usr/bin/env python3
"""Observe exact retained C18 APKs on the named disposable API36 emulator."""
from pathlib import Path
from fresh_android_xml import read_ui_xml
from common_goods_ui import CommonGoodsUi
import argparse,datetime,hashlib,json,os,re,subprocess,time,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--message-suite',required=True);parser.add_argument('--input-suite',required=True);args=parser.parse_args()
OUT=ROOT/'evidence/host-journeys'/datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ');OUT.mkdir(parents=True)
for helper_name in ('common_goods_ui.py', 'fresh_android_xml.py'):
    helper = Path(__file__).with_name(helper_name)
    if helper.is_file():
        (OUT / helper_name).write_bytes(helper.read_bytes())
ADB=Path(os.environ['ANDROID_HOME'])/'platform-tools/adb';PACKAGE='staff.android.builds';events=[];installed=[]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def run(*arguments):
    result=subprocess.run([str(ADB),'-s','emulator-5554',*arguments],capture_output=True,check=True)
    return result.stdout

def xml():
    return read_ui_xml(run, '/sdcard/chapter18-window.xml', {PACKAGE})

ui = CommonGoodsUi(xml, run, OUT)
def capture(name,*expected):
    frames=ui.observe(name)
    content='\n'.join(value for _,value in frames)
    assert all(t in content for t in expected),(name,expected)
    events.append({'screen':name,'viewports':[label for label,_ in frames],
        'expected':expected,'apk_sha256':installed[-1]['sha256'],
        'scope':'Retained scrolling viewports, not one simultaneous screenshot'})
def tap(text):ui.tap(text)
def install(record):
    path=ROOT/record['retained_apk'];expected=record['artifacts']['app/build/outputs/apk/debug/app-debug.apk']
    assert sha(path)==expected
    run('shell','am','force-stop',PACKAGE)
    run('install','-r',str(path));run('shell','pm','clear',PACKAGE)
    run('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(1)
    installed.append({'source':str(path.relative_to(ROOT)),'sha256':expected,'case':record['label']})
try:
    assert 'StaffAndroid_API_36' in run('emu','avd','name').decode()
    message=json.loads((ROOT/args.message_suite/'results.json').read_text())['runs']
    inputs=json.loads((ROOT/args.input_suite/'results.json').read_text())['runs']
    for index,label,wording in [(1,'confirmation-baseline-warm','Maximum quantity reached.'),(5,'confirmation-wording-edit','Choose a smaller quantity.')]:
        install(next(r for r in message if r['label']==label))
        capture(f'{index:02}-{label}-start','Tea in Cart: 3','Build label: Local build')
        tap('Add 1 Tea to Cart');capture(f'{index+1:02}-{label}-accepted','Tea in Cart: 4','Added 1. Cart now 4.')
        tap('Add 1 Tea to Cart');capture(f'{index+2:02}-{label}-rejected','Tea in Cart: 4','Not added: '+wording+' Cart remains 4.')
        tap('Open Cart');capture(f'{index+3:02}-{label}-cart','Tea in Cart: 4','Accepted revision: 1')
    for index,label,expected in [(9,'missing-input-two','One'),(10,'declared-input-two','Two'),(11,'declared-input-three','Three')]:
        install(next(r for r in inputs if r['label']==label))
        capture(f'{index:02}-{label}','Build label: '+expected,'Tea in Cart: 3')
    (OUT/'device.txt').write_bytes(run('shell','getprop'))
    (OUT/'tested-host-journey.py').write_bytes(Path(__file__).read_bytes())
    result={'status':'PASS','installed_artifacts':installed,'events':events,'scope':'Exact retained debug APKs; API36 emulator UI/correctness observations only. Build timings were collected before this emulator was started. The two message APKs are separate additional confirmation runs, not relabeled timed artifacts.'}
except Exception as e:
    result={'status':'FAIL','installed_artifacts':installed,'events':events,'error':str(e)}
    raise
finally:
    (OUT/'result.json').write_text(json.dumps(result,indent=2)+'\n')
    print(OUT)
