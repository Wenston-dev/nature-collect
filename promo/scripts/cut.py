import sys
from rembg import remove, new_session
from PIL import Image
sess = new_session(sys.argv[1])
for name in sys.argv[2:]:
    im = Image.open(f"photos/{name}.jpg").convert("RGB")
    im.thumbnail((1600, 1600))
    out = remove(im, session=sess, post_process_mask=True)
    out.save(f"cut/{name}.png")
    print(name, out.size)
