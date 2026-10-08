import re, glob, os, json, sys
ROOT=os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..'))
SRC=os.path.join(ROOT,'app/src/main/java/app/rayclient')+'/'
SKIP={'Diagnostics.kt','LeakCheck.kt','Brand.kt','I18n.kt','Texts.kt','ProblemReport.kt','ConnWizard.kt'}
CYR=re.compile(r'[А-Яа-яЁё]')

def parse_string(t, i):
    """t[i]=='"'. Возвращает (токены, следующий индекс). Токены: ('s',текст) | ('e',выражение)."""
    assert t[i]=='"'; i+=1; toks=[]; buf=[]
    while i<len(t):
        c=t[i]
        if c=='\\':
            n=t[i+1]
            if n=='n': buf.append('\n'); i+=2
            elif n=='t': buf.append('\t'); i+=2
            elif n=='u': buf.append(chr(int(t[i+2:i+6],16))); i+=6
            else: buf.append(n); i+=2
        elif c=='"':
            if buf: toks.append(('s',''.join(buf)))
            return toks,i+1
        elif c=='$' and t[i+1]=='{':
            if buf: toks.append(('s',''.join(buf))); buf=[]
            depth=1; j=i+2
            while depth>0:
                ch=t[j]
                if ch=='"': _,j=parse_string(t,j); continue
                if ch=='{': depth+=1
                elif ch=='}': depth-=1
                j+=1
            toks.append(('e',t[i+2:j-1])); i=j
        elif c=='$' and (t[i+1].isalpha() or t[i+1]=='_'):
            j=i+1
            while j<len(t) and (t[j].isalnum() or t[j]=='_'): j+=1
            if buf: toks.append(('s',''.join(buf))); buf=[]
            toks.append(('e',t[i+1:j])); i=j
        elif c=='\n': raise ValueError('newline in string')
        else: buf.append(c); i+=1
    raise ValueError('unterminated')

def literals(text):
    i=0; out=[]
    while i<len(text):
        c=text[i]
        if c=='/' and text[i:i+2]=='//': 
            while i<len(text) and text[i]!='\n': i+=1
        elif c=='/' and text[i:i+2]=='/*':
            i=text.index('*/',i)+2
        elif c=="'" :
            i+=3 if text[i+1]!='\\' else 4
        elif c=='"':
            if text[i:i+3]=='"""':
                i=text.index('"""',i+3)+3; continue
            try:
                toks,i=parse_string(text,i); out.append(toks)
                for k,v in toks:
                    if k=='e': out.extend(literals(v))
            except Exception: i+=1
        else: i+=1
    return out

FMT=re.compile(r'%[-0-9.]*[dfsx]')
def norm(toks):
    """-> строка-ключ с {0},{1}..., по строкам."""
    s=''
    for k,v in toks: s+= v if k=='s' else '\x00'
    s=FMT.sub('\x00',s)
    lines=[]
    for ln in s.split('\n'):
        n=[0]
        def rep(m): 
            r='{%d}'%n[0]; n[0]+=1; return r
        lines.append(re.sub('\x00',rep,ln))
    return lines

def collect():
    entries={}
    for f in sorted(glob.glob(SRC+'*.kt')):
        b=os.path.basename(f)
        if b in SKIP: continue
        for toks in literals(open(f,encoding='utf-8').read()):
            for ln in norm(toks):
                if not CYR.search(ln): continue
                raw=ln
                prefix = ln.endswith(' ')
                key=ln.strip().strip('·').strip()
                if not key or not CYR.search(key): continue
                entries.setdefault((key,prefix),set()).add(b)
    return entries

if __name__=='__main__':
    e=collect(); keys=sorted(e.keys(), key=lambda k:(sorted(e[k])[0],k[0]))
    json.dump([{'id':i,'ru':k[0],'prefix':k[1],'files':sorted(e[k])} for i,k in enumerate(keys)],open(os.path.join(os.path.dirname(os.path.abspath(__file__)),'keys.json'),'w'),ensure_ascii=False,indent=0)
    print('ключей:',len(keys),'| с подстановками:',sum('{' in k[0] for k in keys),'| префиксов:',sum(k[1] for k in keys))
