#!/usr/bin/env python3
"""Freeze the C18 source, reports, artifacts and retained investigations once."""
from pathlib import Path
import hashlib,json,shutil,subprocess,xml.etree.ElementTree as ET
ROOT=Path(__file__).resolve().parents[1];REPO=ROOT.parents[1];OUT=ROOT/'evidence/2026-10-06'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
rel=lambda p:str(p.relative_to(REPO))
assert not (OUT/'verification.json').exists(),'Preserve a completed freeze before replacing it'
def copy(p,q):
 q.parent.mkdir(parents=True,exist_ok=True)
 attempt=subprocess.run(['cp','-c',str(p),str(q)],capture_output=True)
 if attempt.returncode:shutil.copy2(p,q)
 assert sha(p)==sha(q)
def tree(p,q):
 for f in sorted(p.rglob('*')):
  if f.is_file():copy(f,q/f.relative_to(p))
counts={}
for name,path,n,f in [('cart','cart-impl/build/test-results/test',16,0),('planner','build-lab/build/test-results/test',31,0),('learner','build-lab/build/test-results/exerciseTest',31,9),('android','app/build/outputs/androidTest-results/connected/debug',8,0)]:
 files=list((ROOT/path).rglob('TEST-*.xml')); suites=[s for p in files for s in ET.parse(p).getroot().iter('testsuite')]
 actual={k:sum(int(s.get(k,'0')) for s in suites) for k in ['tests','failures','errors','skipped']}
 assert actual==dict(tests=n,failures=f,errors=0,skipped=0),(name,actual)
 if f:assert all('AssertionFailedError' in node.get('type','') for p in files for node in ET.parse(p).getroot().iter('failure'))
 counts[name]=actual;tree(ROOT/path,OUT/'reference'/name)
for variant in ['debug','release']:
 p=ROOT/'app/build/reports'/f'lint-results-{variant}.xml';issues=list(ET.parse(p).getroot())
 assert len(issues)==1 and issues[0].get('id')=='MissingApplicationIcon' and issues[0].get('severity')=='Warning'
 copy(p,OUT/'reference'/p.name)
for p in ROOT.glob('evidence-*.log'):copy(p,OUT/'logs'/p.name)
host=ROOT/'evidence/host-journeys/20261006T121336Z';h=json.loads((host/'result.json').read_text())
assert h['status']=='PASS' and len(list(host.glob('*.png')))==11
assert sha(ROOT/'scripts/host-journey.py')==sha(host/'tested-host-journey.py')
for a in h['installed_artifacts']:assert sha(ROOT/a['source'])==a['sha256']
assert 'Ran 3 tests' in (ROOT/'evidence-driver-safety.log').read_text() and 'OK' in (ROOT/'evidence-driver-safety.log').read_text()
artifacts=[]
for path in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/release/app-release-unsigned.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk','cart-api/build/libs/cart-api.jar','cart-impl/build/libs/cart-impl.jar']:
 p=ROOT/path;q=OUT/'artifacts'/p.name;copy(p,q);artifacts.append({'path':rel(q),'sha256':sha(q),'built_from':rel(p)})
sources=[]
for p in sorted(ROOT.rglob('*')):
 local=p.relative_to(ROOT)
 if not p.is_file() or set(local.parts)&{'evidence','build','.gradle','.kotlin','.local-build-cache','__pycache__'}:continue
 if p.name=='local.properties' or p.name.startswith('evidence-'):continue
 if p.name not in {'README.md','verification.md','gradlew','gradlew.bat'} and p.suffix not in {'.kt','.kts','.xml','.toml','.properties','.py','.jar'}:continue
 q=OUT/'source-snapshot'/local;copy(p,q);sources.append({'path':rel(p),'snapshot':rel(q),'sha256':sha(p)})
evidence=[{'path':rel(p),'sha256':sha(p)} for p in sorted((ROOT/'evidence').rglob('*')) if p.is_file() and 'source-snapshot' not in p.relative_to(ROOT/'evidence').parts and p.name!='verification.json']
manifest={'status':'PASS','date':'2026-10-06','edition_cutoff':'2026-10-04','sources':sources,'artifacts':artifacts,'evidence_files':evidence,'reference_checks':{'cart_jvm':16,'planner_jvm':31,'android':8,'total':55},'driver_safety_checks':3,'learner':{'cases':31,'intended_assertion_failures':9,'passes':22,'errors':0,'skipped':0},'test_counts':counts,'host':{'path':rel(host),'paired_captures':11,'installed_artifacts':h['installed_artifacts']},'investigations':{'edit_invocations':72,'edit_measured':24,'cache_invocations':18,'input_invocations':5,'final_confirmation_invocations':4,'earlier_confirmation_invocations':4,'graph_invocations':5},'final_build':{'tasks':297,'executed':160,'from_cache':34,'up_to_date':103},'limits':['One local diagnostic host with small samples, uncontrolled activity and reporting overhead; no general speedup','API36 functional emulator; no physical-device benchmark or API26/API37 execution','No KSP dirty-source counts observed','Declared selected task edges are not full scheduler graph','APK entry-content equality is not archive-byte reproducibility','In-memory cart; no accounts or durable storage','Current safety wrapper differs from preserved driver versions used for earlier measurements']}
(OUT/'verification.json').write_text(json.dumps(manifest,indent=2)+'\n')
print(json.dumps({'status':'PASS','sources':len(sources),'artifacts':len(artifacts),'evidence_files':len(evidence)}))
