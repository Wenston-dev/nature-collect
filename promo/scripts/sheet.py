import sys, glob
from PIL import Image, ImageDraw
d, key, out = sys.argv[1], sys.argv[2], sys.argv[3]
fs = sorted(glob.glob(f"{d}/{key}_*.jpg"))
W = 300; cols = 4; rows = (len(fs) + cols - 1) // cols
sheet = Image.new("RGB", (W * cols, W * rows), "white")
for i, f in enumerate(fs):
    im = Image.open(f).convert("RGB"); im.thumbnail((W - 6, W - 24))
    x, y = (i % cols) * W, (i // cols) * W
    sheet.paste(im, (x + 3, y + 20)); ImageDraw.Draw(sheet).text((x + 4, y + 4), str(i), fill="red")
sheet.save(out)
