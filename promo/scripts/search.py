import json, sys, urllib.request, urllib.parse, os, re, time
UA = "ShiyePromo/1.0 (personal promo video; https://github.com/Wenston-dev/nature-collect)"
def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for i in range(4):
        try:
            return urllib.request.urlopen(req, timeout=60).read()
        except Exception as e:
            time.sleep(2 * (i + 1))
    raise RuntimeError(url)
def search(q, n=12):
    p = {"action": "query", "format": "json", "generator": "search", "gsrsearch": q, "gsrnamespace": 6,
         "gsrlimit": n, "prop": "imageinfo", "iiprop": "url|extmetadata|size|mime", "iiurlwidth": 480}
    d = json.loads(get("https://commons.wikimedia.org/w/api.php?" + urllib.parse.urlencode(p)))
    out = []
    for pg in sorted(d.get("query", {}).get("pages", {}).values(), key=lambda x: x.get("index", 0)):
        ii = pg["imageinfo"][0]
        if ii["mime"] not in ("image/jpeg", "image/png"): continue
        m = ii.get("extmetadata", {})
        lic = m.get("LicenseShortName", {}).get("value", "")
        artist = re.sub("<[^>]+>", "", m.get("Artist", {}).get("value", "")).strip()
        out.append(dict(title=pg["title"], lic=lic, artist=artist, w=ii["width"], h=ii["height"], thumb=ii["thumburl"], url=ii["url"], page=ii["descriptionurl"]))
    return out
if __name__ == "__main__":
    key, q = sys.argv[1], sys.argv[2]
    outdir = sys.argv[3]
    os.makedirs(outdir, exist_ok=True)
    res = search(q, int(sys.argv[4]) if len(sys.argv) > 4 else 12)
    res = [r for r in res if re.search(r"CC0|CC BY|Public domain|PD", r["lic"], re.I) and "NC" not in r["lic"]]
    for i, r in enumerate(res):
        fn = f"{outdir}/{key}_{i:02d}.jpg"
        open(fn, "wb").write(get(r["thumb"]))
        r["file"] = fn
        time.sleep(0.3)
    json.dump(res, open(f"{outdir}/{key}.json", "w"), ensure_ascii=False, indent=1)
    for i, r in enumerate(res): print(i, r["lic"], r["w"], r["h"], r["title"][:80])
