import sys
from PIL import Image, ImageDraw
fs = sys.argv[2:]; out = sys.argv[1]
W, H = 360, 640; cols = 5; rows = (len(fs) + cols - 1) // cols
sh = Image.new('RGB', (W * cols, H * rows), 'white')
for i, f in enumerate(fs):
    im = Image.open(f).convert('RGB').resize((W, H))
    sh.paste(im, ((i % cols) * W, (i // cols) * H)); ImageDraw.Draw(sh).text(((i % cols) * W + 4, (i // cols) * H + 4), f.split('_')[-1], fill='red')
sh.save(out)
