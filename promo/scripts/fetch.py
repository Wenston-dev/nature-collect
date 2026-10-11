import re, json, urllib.request, time, os, io
from PIL import Image
UA = "ShiyePromo/1.0 (personal promo video; contact wgd753@gmail.com)"
picks = {"sparrow1": ("sparrow", 5), "sparrow2": ("sparrow", 2), "sparrow3": ("sparrow", 3), "sparrow4": ("sparrow", 6),
         "bulbul": ("bulbul", 6), "magpie": ("magpie", 6), "violet": ("violet", 4), "ginkgo": ("ginkgo", 4),
         "ladybird": ("ladybird", 9), "dandelion": ("dandelion", 0), "butterfly": ("butterfly", 7),
         "scene": ("scene", 1), "scene_path": ("scene", 4)}
credits = []
for name, (key, i) in picks.items():
    r = json.load(open(f"cand/{key}.json"))[i]
    sz = 1920 if r["w"] > 1920 else 1280 if r["w"] > 1280 else 960
    url = re.sub(r"/\d+px-", f"/{sz}px-", r["thumb"])
    credits.append(dict(name=name, title=r["title"], author=r["artist"], license=r["lic"], page=r["page"]))
    fn = f"photos/{name}.jpg"
    if os.path.exists(fn): continue
    for k in range(8):
        try:
            data = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": UA}), timeout=120).read()
            Image.open(io.BytesIO(data)).size
            open(fn, "wb").write(data); print("ok", name, Image.open(fn).size, flush=True); break
        except Exception as e:
            print("retry", name, e, flush=True); time.sleep(15 * (k + 1))
    time.sleep(6)
json.dump(credits, open("photos/credits.json", "w"), ensure_ascii=False, indent=1)
