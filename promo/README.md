# 拾野宣传片

成片：`out/shiye_promo.mp4`，封面候选：`out/cover.jpg`。

竖版 1080×1920、43.1 秒的小红书宣传片。画面是照着 App 代码（标本风）用 HTML/CSS 复刻的界面，逐帧截图后用 ffmpeg 合成。照片都是 Wikimedia Commons 上的真实照片，没有 AI 生成，作者和授权见 `credits.txt`。

## 重新渲染

需要 Node（带 playwright）、Chromium、ffmpeg。

```bash
./fonts.sh                                           # 下载字体到 site/fonts
node render.mjs still 9.4,21                         # 截几帧到 work/still_*.png 预览
node render.mjs video out/shiye_promo.mp4 30 1.4 44.5  # 渲染整片（从 1.4 秒开始，直接以麻雀开场）
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

## 声音

- 配乐：Sascha Ende《Bright New Day》（CC BY 4.0），截取 0:36 起的 44.5 秒。从 Commons 下载时要用转码版 `.../transcoded/9/99/Bright_New_Day_by_Sascha_Ende.mp3/Bright_New_Day_by_Sascha_Ende.mp3.ogg`，原文件会被 429 限流。
- 音效：`scripts/sfx.py` 按 `site/app.js` 的时间点合成点按、弹出、揭起、提示音、盖章和笔划声，快门声用 Commons 上公有领域的 Holga 录音，输出 `audio/sfx.wav`。
- 混音和合成：

```bash
ffmpeg -ss 36 -t 44.5 -i audio/bright.ogg -i audio/sfx.wav -filter_complex "[0:a]aresample=48000,loudnorm=I=-20:TP=-3,afade=t=in:d=0.4,afade=t=out:st=42.0:d=2.5,volume=0.85[m];[1:a]aresample=48000,pan=stereo|c0=c0|c1=c0,volume=0.9[s];[m][s]amix=inputs=2:normalize=0,loudnorm=I=-15:TP=-1.5[a]" -map "[a]" audio/mix.wav
ffmpeg -i out/shiye_promo.mp4 -i audio/mix.wav -map 0:v -map 1:a -c:v copy  # 配音轨时对 mix.wav 加 -ss 1.4 对齐 -c:a aac -b:a 192k -shortest out/with_audio.mp4
```
