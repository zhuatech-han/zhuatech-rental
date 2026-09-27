#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""Check public artifacts, original QR hashes, owned-source attribution and secret patterns."""
from pathlib import Path
import hashlib
import re
import json
root=Path(__file__).resolve().parents[1]
readme=(root/'README.md').read_text()
images=re.findall(r'!\[[^\]]*\]\(([^)]+)\)',readme)+re.findall(r'<img[^>]+src="([^"]+)"',readme)
for image in images:
    target=root/image
    assert target.is_file() and target.stat().st_size>1000, f'Missing image {image}'
expected={'docs/images/wechat-zhuatech.png':'a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1','docs/images/wechat-zhuatech2.png':'98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215'}
for name,digest in expected.items():
    assert hashlib.sha256((root/name).read_bytes()).hexdigest()==digest,f'Original QR changed {name}'
for value in ['上海如静知华信息科技有限公司','https://www.zhuatech.cn/','zhuatech2','未经书面授权不得商用']:
    assert value in readme and value in (root/'LICENSE').read_text()
patterns=[r'-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----',r'gh[pousr]_[A-Za-z0-9]{30,}',r'github_pat_[A-Za-z0-9_]{40,}',r'AKIA[A-Z0-9]{16}',r'(?i)(?:password|secret|token)\s*[=:]\s*["\'][A-Za-z0-9+/=_-]{18,}["\']']
count=0
for file in root.rglob('*'):
    if not file.is_file() or set(file.relative_to(root).parts)&{'.git','node_modules','target','dist'} or file.suffix in {'.png','.jpg'}:continue
    if file.name.startswith('.env') and file.name!='.env.example':raise AssertionError('Real environment file inside public project')
    text=file.read_text(errors='replace');count+=1
    if file.suffix in {'.java','.js','.vue','.css','.sql'} and '上海如静知华信息科技有限公司' not in text:raise AssertionError(f'Attribution missing {file.relative_to(root)}')
    for pattern in patterns:
        assert not re.search(pattern,text),f'Sensitive pattern in {file.relative_to(root)}'
assert len(list((root/'docs/screenshots').glob('*.png'))) >= 6, 'Six real screenshots required'
assert 'Non-Commercial Source License' in (root/'LICENSE').read_text()
print(json.dumps({'filesScanned':count,'readmeImages':len(images),'sixScreenshots':len(list((root/'docs/screenshots').glob('*.png')))>=6,'originalQrHashes':'PASS','brandLicense':'PASS','sensitivePatternScan':'PASS'}))
