# -*- coding: utf-8 -*-
"""下载三张真实场地照片到 public/images（02 充电站重设计用）"""
import os, ssl, urllib.request

BASE = r"C:\Users\30335\Desktop\wds\vue-demo\public\images"
SHOTS = {
    "station-tianhe.jpg": "https://aka.doubaocdn.com/l/FSa6pJ1Sgz",   # 广州·写字楼健身房·落地窗城市景观
    "station-haizhu.jpg": "https://aka.doubaocdn.com/l/isn2p9VumV",  # 工业风黑砖力量训练区
    "station-panyu.jpg":  "https://aka.doubaocdn.com/l/4EpAwPGJrz",  # 现代明亮工作室（瑜伽/训练）
}

print("PUBLIC/IMAGES NOW:")
for f in sorted(os.listdir(BASE)):
    p = os.path.join(BASE, f)
    if os.path.isfile(p):
        print(" ", f, os.path.getsize(p))

ctx = ssl.create_default_context()
for name, url in SHOTS.items():
    out = os.path.join(BASE, name)
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        with urllib.request.urlopen(req, timeout=60, context=ctx) as r:
            data = r.read()
        with open(out, "wb") as fh:
            fh.write(data)
        print("OK", name, len(data), "bytes", data[:4])
    except Exception as e:
        print("FAIL", name, repr(e))
