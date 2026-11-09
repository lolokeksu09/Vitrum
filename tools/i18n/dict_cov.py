import sys, os, json, re, subprocess
sys.path.insert(0,os.path.dirname(os.path.abspath(__file__)))
import extract
e=extract.collect()
tsv=open(os.path.join(extract.ROOT,'app/src/main/res/raw/i18n_en.tsv'),encoding='utf-8').read().split('\n')
have={(p[1],p[0]=='P') for p in (l.split('\t') for l in tsv if l.strip()) if len(p)==3}
missing=[(k,sorted(v)[0]) for k,v in e.items() if (k[0],k[1]) not in have and (k[0],False) not in have]
print('нет в словаре:',len(missing))
for (ru,pref),f in sorted(missing,key=lambda x:(x[1],x[0])): print(f'{f}|{ru}{" [P]" if pref else ""}')
