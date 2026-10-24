import subprocess,sys,re,os,time
adb=os.path.join(os.environ['LOCALAPPDATA'],'Android','Sdk','platform-tools','adb.exe')
def sh(*a): return subprocess.run([adb,*a],capture_output=True,text=True).stdout
def front():
    for l in sh('shell','dumpsys','activity','activities').splitlines():
        if 'topResumedActivity' in l: return 'app.bheem.bench' in l
    return False
def dump():
    sh('shell','rm','-f','/sdcard/ui.xml')
    if not front(): return ''
    sh('shell','uiautomator','dump','/sdcard/ui.xml')
    x=sh('exec-out','cat','/sdcard/ui.xml')
    return x if 'package="app.bheem.bench"' in x else ''
def nodes():
    x=dump(); out=[]
    for m in re.finditer(r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',x):
        t,x1,y1,x2,y2=m.groups(); out.append((t,(int(x1)+int(x2))//2,(int(y1)+int(y2))//2))
    return out
def tap(text,exact=True):
    for t,x,y in nodes():
        if (t==text) if exact else (text in t):
            sh('shell','input','tap',str(x),str(y)); return True
    return False
def texts(): return [t for t,_,_ in nodes() if t]
def alarms(): return [l.strip() for l in sh('shell','dumpsys','alarm').splitlines() if 'ReminderReceiver' in l]
if __name__=='__main__':
    cmd=sys.argv[1]
    if cmd=='texts': print(texts())
    elif cmd=='tap': print(tap(sys.argv[2]))
    elif cmd=='alarms': print(len(alarms()))
