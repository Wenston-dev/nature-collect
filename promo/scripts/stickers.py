import sys, os
import numpy as np, cv2
from PIL import Image
from rembg import remove, new_session
sess = new_session("isnet-general-use")
os.makedirs("site/img", exist_ok=True)

def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1); return t * t * (3 - 2 * t)

def boxblur(a, r):
    return cv2.blur(a, (2 * r + 1, 2 * r + 1), borderType=cv2.BORDER_CONSTANT)

def outline_overlay(alpha, side=360):
    h, w = alpha.shape; s = side / max(w, h)
    sw, sh = max(1, round(w * s)), max(1, round(h * s))
    inside = cv2.resize(alpha, (sw, sh), interpolation=cv2.INTER_NEAREST) > 0.5
    er = cv2.erode(inside.astype(np.uint8), np.ones((5, 5), np.uint8), borderType=cv2.BORDER_CONSTANT, borderValue=0).astype(bool)
    out = np.zeros((sh, sw, 4), np.uint8); out[..., :3] = 255
    out[..., 3] = np.where(inside & ~er, 255, np.where(inside, 40, 0))
    return Image.fromarray(out)

def edges(img, side=360):
    h, w = img.shape[:2]; s = side / max(w, h)
    small = cv2.resize(img, (round(w * s), round(h * s)), interpolation=cv2.INTER_AREA)
    lum = (0.299 * small[..., 0] + 0.587 * small[..., 1] + 0.114 * small[..., 2]) / 255.0; lum = lum.astype(np.float32)
    gx = cv2.Sobel(lum, cv2.CV_32F, 1, 0, ksize=3); gy = cv2.Sobel(lum, cv2.CV_32F, 0, 1, ksize=3)
    m = np.sqrt(gx * gx + gy * gy); m /= max(m.max(), 1e-3)
    t = np.clip((m - 0.12) / 0.4, 0, 1)
    out = np.zeros(small.shape[:2] + (4,), np.uint8); out[..., :3] = 255; out[..., 3] = (t * 255).astype(np.uint8)
    return Image.fromarray(out)

def sticker(img, alpha):
    ys, xs = np.where(alpha > 0.5)
    x0, x1, y0, y1 = xs.min(), xs.max() + 1, ys.min(), ys.max() + 1
    bw, bh = x1 - x0, y1 - y0
    border = max(6, max(bw, bh) * 0.028); shadow = border * 0.9; margin = round(border + shadow * 2)
    ow, oh = bw + 2 * margin, bh + 2 * margin
    grown = np.zeros((oh, ow), np.float32); grown[margin:margin + bh, margin:margin + bw] = alpha[y0:y1, x0:x1]
    r = max(2, round(border * 0.75))
    ol = smoothstep(0.04, 0.16, boxblur(boxblur(grown, r), r))
    sh = cv2.GaussianBlur(ol, (0, 0), shadow / 2)
    shift = int(round(shadow * 0.6)); sh = np.roll(sh, shift, axis=0)
    # composite: shadow (46 alpha, rgb 40,30,20) -> outline white -> cutout
    A = np.zeros((oh, ow), np.float32); C = np.zeros((oh, ow, 3), np.float32)
    def over(col, a):
        nonlocal A, C
        na = a + A * (1 - a)
        C = np.where(na[..., None] > 0, (col * a[..., None] + C * A[..., None] * (1 - a[..., None])) / np.maximum(na[..., None], 1e-6), 0)
        A = na
    over(np.array([40, 30, 20], np.float32), sh * 46 / 255)
    over(np.array([255, 255, 255], np.float32), ol)
    cut = np.zeros((oh, ow, 3), np.float32); cut[margin:margin + bh, margin:margin + bw] = img[y0:y1, x0:x1]
    over(cut, grown)
    out = np.dstack([np.clip(C, 0, 255), np.clip(A * 255, 0, 255)]).astype(np.uint8)
    return Image.fromarray(out, "RGBA"), (x0, y0, x1, y1, margin)

for name in sys.argv[1:]:
    im = Image.open(f"photos/{name}.jpg").convert("RGB"); im.thumbnail((1600, 1600))
    a = np.array(remove(im, session=sess, only_mask=True, post_process_mask=True)).astype(np.float32) / 255
    img = np.array(im).astype(np.float32)
    st, box = sticker(img, a)
    st.thumbnail((900, 900)); st.save(f"site/img/{name}_sticker.png")
    Image.fromarray((a * 255).astype(np.uint8)).save(f"cut/{name}_mask.png")
    outline_overlay(a).save(f"site/img/{name}_outline.png")
    edges(np.array(im)).save(f"site/img/{name}_edges.png")
    im.save(f"site/img/{name}.jpg", quality=90)
    print(name, im.size, box)
