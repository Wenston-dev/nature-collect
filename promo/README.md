# 拾野宣传片

竖版 1080×1920、44.5 秒的小红书宣传片。画面是照着 App 代码（标本风）用 HTML/CSS 复刻的界面，逐帧截图后用 ffmpeg 合成。照片都是 Wikimedia Commons 上的真实照片，没有 AI 生成，作者和授权见 `credits.txt`。

## 重新渲染

需要 Node（带 playwright）、Chromium、ffmpeg。

```bash
./fonts.sh                                           # 下载字体到 site/fonts
node render.mjs still 9.4,21                         # 截几帧到 work/still_*.png 预览
node render.mjs video out/shiye_promo.mp4 30 0 44.5  # 渲染整片
```

（`render.mjs` 会把截图写到 `work/`，把视频写到 `out/`，需要先建好这两个目录。）

## 改哪里

- `site/app.js`：时间轴。`render(t)` 根据秒数算出每一帧；`CAPS` 是每段字幕和起止时间；各页面的数据（物种、时间线、地图标记、日历、贴纸板）都在文件里。
- `site/index.html`：各页面的布局和样式，单位是 dp（手机屏幕按 360×780 dp 放大 1.9 倍）。
- `site/img/`：已经生成好的照片、贴纸、扫描用的边缘图和候选蒙版。

## 重新生成素材（可选）

- `scripts/search.py`：在 Commons 搜图，结果写到 `cand/`。
- `scripts/fetch.py`：按 `cand/` 里选中的编号下载原图到 `photos/`。注意要用标准缩略图宽度，否则会被 429 限流。
- `scripts/stickers.py <名字...>`：用 rembg（isnet-general-use）抠图，按 `StickerMaker` 的规则加白边和阴影，同时输出边缘图和蒙版。
- 地图是 OpenStreetMap 西湖一带 z14 瓦片拼成的 `site/img/map.png`。
