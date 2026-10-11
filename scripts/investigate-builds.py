#!/usr/bin/env python3
"""Run bounded, source-restoring C18 build experiments. No daemon stop/clean in edit cases."""
from pathlib import Path
from datetime import datetime, timezone
import argparse, hashlib, json, os, platform, re, shutil, statistics, subprocess, time, zipfile

ROOT = Path(__file__).resolve().parents[1]
JDK = Path(os.environ.get('JAVA_HOME', ''))
if not os.environ.get('JAVA_HOME') or not (JDK/'bin/java').is_file():
    raise SystemExit('Set JAVA_HOME to a JDK 27 installation before running this driver.')
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
MODULES = ['app','feature-cart','feature-saved','cart-api','cart-impl','build-lab']
ACTIVE_SUITE = None
FOCUS = [':cart-api:compileKotlin', ':cart-impl:compileKotlin', ':feature-cart:compileDebugKotlin', ':feature-saved:compileDebugKotlin', ':app:kspDebugKotlin', ':app:compileDebugKotlin', ':app:mergeProjectDexDebug', ':app:packageDebug', ':app:generateDebugBuildLabel']

def source_files():
    files = [ROOT/n for n in ['settings.gradle.kts','build.gradle.kts','gradle.properties','gradle/libs.versions.toml','gradle/wrapper/gradle-wrapper.properties']]
    for base in [ROOT/m for m in MODULES]+[ROOT/'build-logic']:
        files += [p for p in base.rglob('*') if p.is_file() and 'build' not in p.relative_to(base).parts and '.gradle' not in p.relative_to(base).parts and (p.suffix in ['.kt','.kts','.xml','.properties'])]
    return sorted(set(files))

def environment():
    def read(*command):
        try:
            p = subprocess.run(command,capture_output=True,text=True)
            return {'exit_code':p.returncode,'stdout':p.stdout.strip(),'stderr':p.stderr.strip()}
        except FileNotFoundError:
            return {'exit_code':None,'stdout':'','stderr':'This host has no '+command[0]}
    return {'date':datetime.now(timezone.utc).isoformat(),'platform':platform.platform(),'java_home':os.environ.get('JAVA_HOME',str(JDK)),
      'java_version':read(str(JDK/'bin/java'),'-version'),
      'gradle_version':read(str(ROOT/'gradlew'),'--version','--console=plain'),
      'cpu_memory_cores':read('sysctl','-n','machdep.cpu.brand_string','hw.memsize','hw.ncpu'),
      'os':read('sw_vers'),'power':read('pmset','-g','batt'),'thermal':read('pmset','-g','therm'),
      'scope':'One local Mac; diagnostic --info/--profile/Kotlin reporting overhead included; small synthetic commerce fixture. No emulator during timings; no controlled idle/thermal laboratory or universal speedup claim.',
      'workers':2,'parallel_projects':False,'heap':'2g','network':'--offline; existing dependency downloads retained','daemon':'Reused; never stopped in this suite; warmup runs explicitly recorded',
      'task_output_cache':'Disabled for edit comparisons; dedicated local cache for restore experiment','configuration_cache':'Enabled for edit comparisons; separately disabled in configuration comparison'}

class Suite:
    def __init__(self, name):
        global ACTIVE_SUITE
        ACTIVE_SUITE = self
        self.edited_states = {}
        self.out = ROOT/'evidence'/'investigations'/(datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+name)
        self.out.mkdir(parents=True)
        self.runs=[]
        shutil.copy2(Path(__file__),self.out/'executed-driver.py')
        (self.out/'executed-driver.json').write_text(json.dumps({'sha256':sha(Path(__file__))},indent=2)+'\n')
        self.env=os.environ.copy();self.env['JAVA_HOME']=os.environ.get('JAVA_HOME',str(JDK))
        (self.out/'environment-before.json').write_text(json.dumps(environment(),indent=2)+'\n')
        self.baseline={p:p.read_bytes() for p in source_files()}
        for p,data in self.baseline.items():
            q=self.out/'source-baseline'/p.relative_to(ROOT);q.parent.mkdir(parents=True,exist_ok=True);q.write_bytes(data)
    def restore(self):
        conflicts = []
        for p, states in self.edited_states.items():
            current = p.read_bytes() if p.exists() else None
            if current == self.baseline[p]:
                continue
            if current in states:
                p.write_bytes(self.baseline[p])
            else:
                conflicts.append(str(p))
        if conflicts:
            raise RuntimeError('Preserved concurrent source edits; restore manually from the saved baseline: '+', '.join(conflicts))
    def run(self,label,args,measured=False,description=''):
        index=len(self.runs)+1;directory=self.out/f'{index:03}-{label}';directory.mkdir()
        command=['./gradlew',*args,'--offline','--profile','--info','--console=plain']
        before={str(p.relative_to(ROOT)):sha(p) for p in source_files()}
        for p,data in self.baseline.items():
            if p.read_bytes()!=data:
                q=directory/'changed-source'/p.relative_to(ROOT);q.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(p,q)
        started_ns=time.time_ns();start=time.perf_counter()
        with (directory/'gradle.log').open('w') as log:
            completed=subprocess.run(command,cwd=ROOT,env=self.env,stdout=log,stderr=subprocess.STDOUT)
        elapsed=time.perf_counter()-start
        text=(directory/'gradle.log').read_text()
        outcomes={}
        for line in text.splitlines():
            match=re.fullmatch(r'> Task (:[^ ]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?',line)
            if match:outcomes[match[1]]=match[2] or 'EXECUTED'
        reports=[];kotlin=[]
        for base in [ROOT/'build/reports/profile', ROOT/'build/reports/kotlin-build',ROOT/'app/build/kspCaches/debug/logs']:
            if not base.exists():continue
            for file in base.rglob('*'):
                if file.is_file() and file.stat().st_mtime_ns>=started_ns:
                    destination=directory/'reports'/file.relative_to(ROOT);destination.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(file,destination)
                    reports.append({'path':str(destination.relative_to(ROOT)),'sha256':sha(destination)})
                    if base.name=='kotlin-build' and file.suffix=='.json':
                        data=json.loads(file.read_text())
                        kotlin += [{'path':x['path'],'totalTimeMs':x.get('totalTimeMs'),'icLogLines':x.get('icLogLines',[]),'buildMetrics':x.get('buildMetrics',{})} for x in data.get('buildOperationRecord',[]) if x['path'] in FOCUS]
        # Preserve profile CSS/JS so copied HTML is independently readable.
        for sub in ['css','js']:
            src=ROOT/'build/reports/profile'/sub
            if src.exists():shutil.copytree(src,directory/'reports/build/reports/profile'/sub,dirs_exist_ok=True)
        apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
        artifacts={}
        retained_apk = None
        if apk.exists() and ('input-' in label or label.startswith('confirmation-')):
            retained_apk=directory/'app-debug.apk'
            subprocess.run(['cp','-c',str(apk),str(retained_apk)],check=True) if platform.system()=='Darwin' else shutil.copy2(apk,retained_apk)
        for p in [apk,ROOT/'cart-api/build/libs/cart-api.jar',ROOT/'cart-impl/build/libs/cart-impl.jar']:
            if p.exists():artifacts[str(p.relative_to(ROOT))]=sha(p)
        classes={}
        for module in ['feature-cart','feature-saved']:
            base=ROOT/module/'build/intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes'
            classes[module]={str(p.relative_to(base)):sha(p) for p in sorted(base.rglob('*.class'))} if base.exists() else {}
        generated=ROOT/'app/build/generated/build-label/debug/build-info.txt'
        record={'index':index,'label':label,'measured':measured,'description':description,'command':command,'exit_code':completed.returncode,'wall_seconds':elapsed,
          'source_sha256':before,'source_after_sha256':{str(p.relative_to(ROOT)):sha(p) for p in source_files()},'configuration_cache_reused':'Reusing configuration cache.' in text,
          'task_outcomes':outcomes,'focused_outcomes':{task:outcomes.get(task,'NOT_LISTED') for task in FOCUS},'kotlin_records':kotlin,'reports':reports,'artifacts':artifacts,'feature_classes':classes,
          'retained_apk':str(retained_apk.relative_to(ROOT)) if retained_apk else None,
          'generated_label':generated.read_text().strip() if generated.exists() else None,'apk_label':zipfile.ZipFile(apk).read('assets/build-info.txt').decode().strip() if apk.exists() else None}
        assert record['source_sha256']==record['source_after_sha256'],'Source changed during build'
        (directory/'run.json').write_text(json.dumps(record,indent=2)+'\n');self.runs.append(record)
        (self.out/'results.json').write_text(json.dumps({'runs':self.runs},indent=2)+'\n')
        print(json.dumps({'run':label,'exit':completed.returncode,'seconds':round(elapsed,3),'cc':record['configuration_cache_reused'],'focus':record['focused_outcomes']}),flush=True)
        if completed.returncode:raise RuntimeError(f'Build failed; see {directory}')
        return record
    def close(self):
        self.restore()
        (self.out/'environment-after.json').write_text(json.dumps(environment(),indent=2)+'\n')
        (self.out/'source-restoration.json').write_text(json.dumps({str(p.relative_to(ROOT)):sha(p) for p in self.baseline},indent=2)+'\n')

IMPL='cart-impl/src/main/kotlin/staff/android/builds/impl/DefaultCart.kt'
API='cart-api/src/main/kotlin/staff/android/builds/api/CartRepository.kt'
SAVED='feature-saved/src/main/kotlin/staff/android/builds/savedui/SavedScreen.kt'
LABEL='build-logic/src/main/kotlin/staff/buildlogic/GenerateBuildLabel.kt'
EDITS={
 'private-body':(IMPL,'"Maximum quantity reached."','"Choose a smaller quantity."'),
 'implementation-abi':(IMPL,'    initial: List<CartItem>,\n): CartRepository','    initial: List<CartItem>,\n    auditEnabled: Boolean = false,\n): CartRepository'),
 'contract-abi':(API,'val message: String,','val message: CharSequence,'),
 'saved-caption':(SAVED,'"Tea is saved for later. Saving does not add it to Cart."','"Saved Tea is ready when you need it. Saving does not add it to Cart."'),
}
def edit(path,old,new):
    p=ROOT/path
    before=p.read_bytes()
    if ACTIVE_SUITE is None or p not in ACTIVE_SUITE.baseline:
        raise RuntimeError('Edits require a captured suite baseline')
    allowed = ACTIVE_SUITE.edited_states.setdefault(p, set())
    if before != ACTIVE_SUITE.baseline[p] and before not in allowed:
        raise RuntimeError('Preserved concurrent source change: '+str(p))
    text=before.decode();assert text.count(old)==1,(path,old)
    after=text.replace(old,new).encode()
    allowed.add(after)
    p.write_bytes(after)

def edit_suite(suite,repeats):
    for case,(path,old,new) in EDITS.items():
        for repetition in range(1,repeats+1):
            modes=['narrow','wide'] if repetition%2 else ['wide','narrow']
            for mode in modes:
                suite.restore();args=[':app:assembleDebug',f'-Pboundary={mode}','--no-build-cache','--configuration-cache']
                suite.run(f'{case}-{mode}-{repetition}-prime1',args,description='Restore baseline then establish outputs and this requested graph')
                suite.run(f'{case}-{mode}-{repetition}-prime2',args,description='No-edit warmup; establish configuration reuse and retained outputs')
                edit(path,old,new)
                suite.run(f'{case}-{mode}-{repetition}-edit',args,True,'One source edit; no clean, no task-output cache, retained incremental state')

def cache_suite(suite,repeats):
    base=[':app:assembleDebug','-Pboundary=narrow','--no-build-cache']
    for enabled in [False,True]:
        flag='--configuration-cache' if enabled else '--no-configuration-cache'
        suite.run(f'configuration-{enabled}-warmup1',base+[flag])
        suite.run(f'configuration-{enabled}-warmup2',base+[flag])
    for i in range(1,repeats+1):
        for enabled in ([False,True] if i%2 else [True,False]):
            suite.run(f'configuration-{enabled}-{i}',base+['--configuration-cache' if enabled else '--no-configuration-cache'],True,'No source edit; same retained task outputs, change configuration-cache mode only')
    args=[':app:assembleDebug','-Pboundary=narrow','--build-cache','--configuration-cache']
    suite.run('restore-cache-populate',args+['--rerun-tasks'],description='Populate dedicated local task-output cache; not an edit-build sample')
    suite.run('restore-no-edit-control',args,True)
    removed=[]
    for module in ['app','feature-cart','feature-saved','cart-api','cart-impl']:
        directory=ROOT/module/'build'
        if directory.exists():shutil.rmtree(directory);removed.append(str(directory.relative_to(ROOT)))
    (suite.out/'deleted-output-directories.json').write_text(json.dumps(removed,indent=2)+'\n')
    suite.run('restore-absent-outputs',args,True,'Delete only five production modules build directories; retain local task cache, project history, build-logic outputs and downloaded dependencies')
    suite.run('restore-afterwards-up-to-date',args,True)

def input_suite(suite):
    args=[':app:assembleDebug','-Pboundary=narrow','--no-build-cache','--configuration-cache']
    edit(LABEL,'@get:Input','@get:org.gradle.api.tasks.Internal')
    one=suite.run('missing-input-one',args+['-PbuildLabel=One'])
    two=suite.run('missing-input-two',args+['-PbuildLabel=Two'])
    assert one['apk_label']=='One' and two['apk_label']=='One'
    assert two['task_outcomes'][':app:generateDebugBuildLabel']=='UP-TO-DATE'
    suite.restore()
    repaired=suite.run('declared-input-two',args+['-PbuildLabel=Two'])
    assert repaired['apk_label']=='Two'
    three=suite.run('declared-input-three',args+['-PbuildLabel=Three'])
    assert three['apk_label']=='Three' and three['task_outcomes'][':app:generateDebugBuildLabel']=='EXECUTED'
    suite.run('declared-input-three-again',args+['-PbuildLabel=Three'])

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('suite',choices=['edits','caches','inputs']);parser.add_argument('--repeats',type=int,default=3);args=parser.parse_args()
    assert args.repeats>0
    suite=Suite(args.suite)
    try:
        {'edits':lambda:edit_suite(suite,args.repeats),'caches':lambda:cache_suite(suite,args.repeats),'inputs':lambda:input_suite(suite)}[args.suite]()
    finally:suite.close()
    print(suite.out)
