// Deterministic timeline: window.render(t) draws the frame at t seconds.
'use strict';
const $ = (id) => document.getElementById(id);
const clamp = (x, a = 0, b = 1) => Math.min(b, Math.max(a, x));
const lerp = (a, b, k) => a + (b - a) * k;
const seg = (t, a, b) => clamp((t - a) / (b - a));
function bezier(x1, y1, x2, y2) {
  return (x) => {
    if (x <= 0) return 0; if (x >= 1) return 1;
    let lo = 0, hi = 1, s = x;
    for (let i = 0; i < 30; i++) {
      s = (lo + hi) / 2;
      const cx = 3 * x1 * s * (1 - s) ** 2 + 3 * x2 * s * s * (1 - s) + s ** 3;
      if (cx < x) lo = s; else hi = s;
    }
    return 3 * y1 * s * (1 - s) ** 2 + 3 * y2 * s * s * (1 - s) + s ** 3;
  };
}
const FOS = bezier(0.4, 0, 0.2, 1);      // FastOutSlowIn
const LOS = bezier(0, 0, 0.2, 1);        // LinearOutSlowIn
const EIO = bezier(0.42, 0, 0.58, 1);
// Compose-style spring from 0 to 1 (mass 1).
function spring(t, z = 1, k = 1500) {
  if (t <= 0) return 0;
  const w = Math.sqrt(k);
  if (z < 1) {
    const wd = w * Math.sqrt(1 - z * z);
    return 1 - Math.exp(-z * w * t) * (Math.cos(wd * t) + (z * w / wd) * Math.sin(wd * t));
  }
  return 1 - Math.exp(-w * t) * (1 + w * t);
}
function css(el, o) { for (const k in o) el.style[k] = o[k]; }
function h(html) { const d = document.createElement('div'); d.innerHTML = html.trim(); return d.firstChild; }

// ---------- paper grain (same recipe as SpecimenCanvas.grain) ----------
(function grain() {
  const c = document.createElement('canvas'); c.width = c.height = 192;
  const g = c.getContext('2d'); const im = g.createImageData(192, 192);
  let seed = 7; const rnd = () => ((seed = (seed * 1103515245 + 12345) & 0x7fffffff) / 0x7fffffff);
  for (let i = 0; i < 192 * 192; i++) {
    const a = rnd() < 0.2 ? 6 + Math.floor(rnd() * 16) : 0;
    im.data[i * 4] = 0x22; im.data[i * 4 + 1] = 0x39; im.data[i * 4 + 2] = 0x2f; im.data[i * 4 + 3] = a;
  }
  g.putImageData(im, 0, 0);
  document.documentElement.style.setProperty('--grain', `url(${c.toDataURL()})`);
  document.querySelectorAll('.grain').forEach((e) => { e.style.backgroundSize = '96px 96px'; });
})();

// ---------- stamps ----------
const RUST = '#B4532E';
function dateStampSVG(svg, size, year, day) { // ui/Specimen.kt DateStamp
  const r = size / 2, c = r;
  svg.setAttribute('viewBox', `0 0 ${size} ${size}`);
  svg.innerHTML = `<g transform="rotate(-14 ${c} ${c})" fill="${RUST}" fill-opacity=".8" stroke="${RUST}" stroke-opacity=".8">
    <circle cx="${c}" cy="${c}" r="${r - 1}" fill="none" stroke-width="1.5"/>
    <circle cx="${c}" cy="${c}" r="${r - 4}" fill="none" stroke-width=".7"/>
    <g stroke="none" text-anchor="middle">
    <text x="${c}" y="${c - r * 0.22}" font-size="${r * 0.30}" font-weight="700" font-family="Noto Sans SC">拾野</text>
    <text x="${c}" y="${c + r * 0.12}" font-size="${r * 0.26}" font-family="DM Mono">${year}</text>
    <text x="${c}" y="${c + r * 0.44}" font-size="${r * 0.26}" font-family="DM Mono">${day}</text></g></g>`;
}
function shareStampSVG(svg, size, lines) { // export/ShareCard.kt SpecimenCanvas.stamp
  const r = size / 2, c = r, sw = r * 0.045, step = r * 0.34, first = c - step * (lines.length - 1) / 2 + r * 0.1;
  let txt = '';
  lines.forEach((l, i) => {
    const cjk = /[⺀-￿]/.test(l);
    txt += `<text x="${c}" y="${first + i * step}" font-size="${cjk ? r * 0.30 : r * 0.26}" ${cjk ? 'font-weight="700" font-family="Noto Sans SC"' : 'font-family="DM Mono"'}>${l}</text>`;
  });
  svg.setAttribute('viewBox', `0 0 ${size} ${size}`);
  svg.innerHTML = `<g transform="rotate(-14 ${c} ${c})" stroke="${RUST}" stroke-opacity=".82" fill="none">
    <circle cx="${c}" cy="${c}" r="${r - sw}" stroke-width="${sw}"/><circle cx="${c}" cy="${c}" r="${r * 0.86}" stroke-width="${r * 0.02}"/>
    <g stroke="none" fill="${RUST}" fill-opacity=".82" text-anchor="middle">${txt}</g></g>`;
}
function collectStampSVG(size) { // BoardScreen.kt CollectStamp
  const r = size / 2, c = r;
  return `<svg width="${size}" height="${size}" viewBox="0 0 ${size} ${size}"><g transform="rotate(-14 ${c} ${c})" stroke="${RUST}" stroke-opacity=".8" fill="none">
    <circle cx="${c}" cy="${c}" r="${r - 1}" stroke-width="1.4"/><circle cx="${c}" cy="${c}" r="${r - 3.5}" stroke-width=".6"/>
    <g stroke="none" fill="${RUST}" fill-opacity=".8" text-anchor="middle" font-family="Noto Sans SC" font-weight="700" font-size="${r * 0.36}">
    <text x="${c}" y="${c - r * 0.06}">拾野</text><text x="${c}" y="${c + r * 0.40}">采集</text></g></g></svg>`;
}
dateStampSVG($('savedStamp'), 60, '2026', '10.11');
shareStampSVG($('shareStamp'), 156, ['拾野', '2026', '10.11']);

// ---------- data ----------
const SP = {
  sparrow1: { n: '麻雀', no: 1, cat: '鸟类', key: 'BIRD', cnt: 4 },
  ginkgo: { n: '银杏', no: 8, cat: '植物', key: 'PLANT', cnt: 1 },
  magpie: { n: '喜鹊', no: 4, cat: '鸟类', key: 'BIRD', cnt: 1 },
  violet: { n: '二月兰', no: 3, cat: '花卉', key: 'FLOWER', cnt: 3 },
  bulbul: { n: '白头鹎', no: 2, cat: '鸟类', key: 'BIRD', cnt: 2 },
  ladybird: { n: '七星瓢虫', no: 5, cat: '昆虫', key: 'INSECT', cnt: 1 },
  dandelion: { n: '蒲公英', no: 6, cat: '植物', key: 'PLANT', cnt: 2 },
  butterfly: { n: '菜粉蝶', no: 7, cat: '昆虫', key: 'INSECT', cnt: 1 },
};
const IMG = {}; // natural sizes of stickers
const stickerURL = (k) => `img/${k}_sticker.png`;
function fitBox(k, w, hh) { // contain-fit sticker k into w x h, returns {w,h,x,y}
  const s = IMG[k]; const sc = Math.min(w / s.w, hh / s.h);
  return { w: s.w * sc, h: s.h * sc, x: (w - s.w * sc) / 2, y: (hh - s.h * sc) / 2 };
}
function stickerEl(k, w, hh, extra = '') {
  return `<div class="sticker" style="left:0;top:0;width:${w}px;height:${hh}px;background-image:url(${stickerURL(k)});${extra}"></div>`;
}

// ---------- chips ----------
function chips(el, list, on) {
  el.innerHTML = list.map((c) => `<span class="chip${c === on ? ' on' : ''}" data-c="${c}">${c}${c === on ? '<i class="ul"></i>' : ''}</span>`).join('');
}
chips($('collChips'), ['全部', '鸟类', '花卉', '植物', '昆虫', '＋ 分类'], '全部');
chips($('mapChips'), ['全部', '鸟类', '花卉', '植物', '昆虫', '动物', '其他'], '全部');
chips($('calChips'), ['全部', '鸟类', '花卉', '植物', '昆虫', '动物', '其他'], '全部');

// ---------- collection grid ----------
const ORDER = ['sparrow1', 'ginkgo', 'magpie', 'violet', 'bulbul', 'ladybird', 'dandelion', 'butterfly'];
const BIRDS = ['sparrow1', 'magpie', 'bulbul'];
const cellPos = (i) => ({ x: 18 + (i % 2) * 168, y: 218 + Math.floor(i / 2) * 186 });
const cells = {};
function buildGrid() {
  const g = $('grid');
  ORDER.forEach((k, i) => {
    const s = SP[k];
    const e = h(`<div class="cell card"><div class="art"></div><div class="hl"></div>
      <div class="lbl"><span class="nm">${s.n}</span><span class="meta">No.${String(s.no).padStart(3, '0')} · ×${s.cnt}</span></div></div>`);
    e.querySelector('.art').innerHTML = stickerEl(k, 128, 117.8);
    g.appendChild(e); cells[k] = e;
  });
}

// ---------- detail ----------
function detailHTML(o) {
  const tl = o.timeline.map((r, i) => {
    const cur = r.k === o.k, first = i === 0, last = i === o.timeline.length - 1;
    const dot = cur
      ? `<svg class="abs" style="left:4px;top:24px" width="12" height="12"><circle cx="6" cy="6" r="6" fill="#FBFBF7"/><circle cx="6" cy="6" r="5" fill="none" stroke="${RUST}" stroke-width="1.5"/><circle cx="6" cy="6" r="2" fill="${RUST}"/></svg>`
      : `<svg class="abs" style="left:6px;top:26px" width="8" height="8"><circle cx="4" cy="4" r="4" fill="#FBFBF7"/><circle cx="4" cy="4" r="3" fill="#6F8277"/></svg>`;
    return `<div class="trow" data-i="${i}"><div class="rail">${first ? '' : '<i class="ln" style="top:0;height:30px"></i>'}${last ? '' : '<i class="ln" style="top:30px;height:30px"></i>'}${dot}</div>
      <div class="art">${stickerEl(r.k, 48, 48)}</div>
      <div><div class="d" style="${cur ? 'color:var(--pine);font-weight:500' : ''}">${r.d}</div><div class="p">${r.p}</div></div></div>`;
  }).join('');
  return `<div class="abs" style="left:0;top:0;width:360px">
    <div style="height:64px;display:flex;align-items:center;justify-content:space-between;padding:8px">
      <span class="hbtn" style="width:48px;height:48px"><i class="icon" style="-webkit-mask-image:url(icons/arrow_back.svg)"></i></span>
      <span style="display:flex">${['file_download', 'auto_awesome_mosaic', 'share', 'more_vert'].map((n) => `<span class="hbtn" style="width:48px;height:48px"><i class="icon" style="-webkit-mask-image:url(icons/${n}.svg)"></i></span>`).join('')}</span></div>
    <div style="padding:0 16px">
      <div class="card" style="margin-top:4px;padding:16px">
        <div style="position:relative;width:296px;height:296px">
          <div class="dsticker" style="position:absolute;left:36px;top:36px;width:224px;height:224px;transform:rotate(${o.tilt}deg)">${stickerEl(o.k, 224, 224)}<div class="holo"></div></div>
          <svg class="dstamp abs" style="right:0;top:0" width="62" height="62"></svg>
        </div>
        <div style="height:12px"></div>
        <div style="display:flex;align-items:flex-end">
          <div style="width:112.5px;padding-right:12px;display:flex;flex-direction:column;gap:2px">
            <div style="display:flex;align-items:center"><i style="width:6px;height:6px;border-radius:50%;background:#3D7DCA;display:block"></i><span style="margin-left:6px;font-size:12px;font-weight:500">鸟类</span></div>
            <div style="font-size:12px;color:var(--soft)">把握 ${o.conf}%</div>
            <div style="width:32px;height:32px;display:flex;align-items:center;justify-content:center;color:var(--soft)"><i class="icon" style="width:16px;height:16px;-webkit-mask-image:url(icons/refresh.svg)"></i></div>
          </div>
          <div class="hlabel" style="flex:1">
            <div class="head">No.001 · BIRD</div>
            <div class="name">麻雀<i class="icon" style="width:14px;height:14px;margin-left:4px;color:var(--soft);-webkit-mask-image:url(icons/edit.svg)"></i></div>
            <div class="sci">Passer montanus</div>
            <div class="dash"></div>
            ${o.rows.map((r, i) => `<div class="r"><span class="k">${r[0]}</span><span class="v">${r[1]}</span>${i === 0 ? '<i class="icon" style="width:12px;height:12px;color:var(--soft);-webkit-mask-image:url(icons/edit.svg)"></i>' : ''}</div>`).join('')}
          </div>
        </div>
        <div style="height:16px"></div><div style="height:.5px;background:var(--divider)"></div><div style="height:8px"></div>
        <div style="display:flex;align-items:flex-start;padding:4px 0"><span style="flex:1;font-size:14px;line-height:20px">常见的小型鸟，头顶栗褐色，白脸颊上有一块黑斑，常成群在地面啄食。</span>
          <i class="icon" style="width:14px;height:14px;margin:2px 0 0 8px;color:var(--soft);-webkit-mask-image:url(icons/edit.svg)"></i></div>
        <div style="height:10px"></div>
        <div style="font-size:12px;font-weight:500;color:var(--soft)">也可能是：</div>
        <div style="display:flex;gap:4px"><span class="chip">家麻雀</span><span class="chip">山麻雀</span></div>
      </div>
      <div style="height:16px"></div>
      <div class="card tl" style="padding:14px 12px">
        <div style="display:flex;align-items:flex-end;padding:0 4px"><span style="font-size:17px;font-weight:500;letter-spacing:.5px">足迹</span>
          <span class="mono" style="font-size:11px;letter-spacing:.6px;color:var(--soft);margin:0 0 3px 10px">4 次 · 4 个地点</span></div>
        <div style="height:10px"></div><div style="height:.5px;background:var(--divider)"></div>
        ${tl}
      </div>
      <div style="height:32px"></div>
    </div></div>`;
}
const TIMELINE = [
  { k: 'sparrow1', d: '2026.10.11', p: '西湖 · 孤山' },
  { k: 'sparrow2', d: '2026.09.20', p: '曲院风荷' },
  { k: 'sparrow3', d: '2026.06.14', p: '柳浪闻莺' },
  { k: 'sparrow4', d: '2026.04.03', p: '太子湾公园' },
];
let detA, detB;
function buildDetail() {
  const inner = $('detInner');
  detA = h(detailHTML({ k: 'sparrow1', tilt: -2.5, conf: 94, timeline: TIMELINE,
    rows: [['地点', '西湖 · 孤山'], ['时间', '2026.10.11 08:42'], ['坐标', '30.2563N 120.1452E'], ['遇见', '第 4 次']] }));
  detB = h(detailHTML({ k: 'sparrow4', tilt: 3, conf: 91, timeline: TIMELINE,
    rows: [['地点', '太子湾公园'], ['时间', '2026.04.03 07:15'], ['坐标', '30.2336N 120.1477E'], ['遇见', '第 1 次']] }));
  inner.appendChild(detA); inner.appendChild(detB);
  dateStampSVG(detA.querySelector('.dstamp'), 62, '2026', '10.11');
  dateStampSVG(detB.querySelector('.dstamp'), 62, '2026', '04.03');
  [detA, detB].forEach((d) => {
    const holo = d.querySelector('.holo');
    const k = d === detA ? 'sparrow1' : 'sparrow4';
    css(holo, { webkitMaskImage: `url(${stickerURL(k)})` });
  });
}

// ---------- map ----------
// Marker anchors in z15 pixels of the fetched area (map.png is the same area at z14, half size).
const MARKS = [
  ['bulbul', 370, 440], ['dandelion', 540, 480], ['sparrow1', 300, 640], ['ginkgo', 760, 600],
  ['sparrow2', 95, 720], ['ladybird', 125, 790], ['sparrow3', 630, 990], ['magpie', 830, 1060],
  ['violet', 345, 1262], ['butterfly', 262, 1300], ['sparrow4', 305, 1288],
];
const markerEls = [];
function buildMap() {
  const box = $('markers');
  css(box, { transform: 'none', width: '316px', height: '513.5px' });
  MARKS.forEach(([k]) => {
    const f = fitBox(k, 52, 52);
    const e = h(`<div class="abs" style="width:${f.w}px;height:${f.h}px;background:url(${stickerURL(k)}) center/contain no-repeat;filter:drop-shadow(0 1.2px 2.5px rgba(30,50,40,.25))"></div>`);
    box.appendChild(e); markerEls.push({ e, f });
  });
  $('placeGrid').innerHTML = ['sparrow4', 'butterfly', 'violet'].map((k) => `<div style="width:102.7px;display:flex;flex-direction:column;align-items:center">
      <div style="position:relative;width:90.7px;height:90.7px;margin:6px">${stickerEl(k, 90.7, 90.7)}</div>
      <div style="font-size:12px;text-align:center">${k === 'violet' ? '二月兰' : SP[k] ? SP[k].n : '麻雀'}</div></div>`).join('');
}
function mapView(s) { // s = z14 scale; centre the area of interest
  const cx = 240, cy = 425;
  return { s, left: 158 - cx * s, top: 256.75 - cy * s };
}

// ---------- calendar ----------
const DAYS = { 3: ['ginkgo'], 5: ['magpie'], 8: ['ladybird'], 11: ['sparrow1', 'dandelion'] };
function buildMonth() {
  const w = 316 / 7, hh = w / 0.9;
  let html = `<div style="display:flex;padding:8px 0">${'日一二三四五六'.split('').map((d) => `<span style="flex:1;text-align:center;font-size:12px;font-weight:500;color:var(--soft)">${d}</span>`).join('')}</div>`;
  const cellsArr = Array(4).fill(null).concat([...Array(31)].map((_, i) => i + 1));
  for (let r = 0; r < Math.ceil(cellsArr.length / 7); r++) {
    html += '<div style="display:flex">';
    for (let c = 0; c < 7; c++) {
      const d = cellsArr[r * 7 + c];
      let inner = '';
      if (d) {
        const caps = DAYS[d];
        if (caps) {
          inner = `<div class="abs" style="left:4px;top:9px;width:${w - 12}px;height:${hh - 16}px">${stickerEl(caps[0], w - 12, hh - 16)}</div>
            <span class="abs mono" style="left:3px;top:1px;font-size:9px;color:${d === 11 ? RUST : 'var(--soft)'}">${d}</span>
            ${caps.length > 1 ? `<span class="abs mono" style="right:3px;top:1px;font-size:9px;font-weight:500">${caps.length}</span>` : ''}`;
        } else {
          inner = `<span class="mono" style="font-size:13px">${d}</span>`;
        }
      }
      html += `<div style="width:${w}px;height:${hh}px;padding:2px;position:relative"><div class="dc" data-d="${d || ''}" style="position:relative;width:100%;height:100%;display:flex;align-items:center;justify-content:center">${inner}</div></div>`;
    }
    html += '</div>';
  }
  $('month').innerHTML = html;
}
const SPOTS = [[0.02, 0.04, -6], [0.70, 0.02, 5], [0.02, 0.52, 4], [0.72, 0.50, -4]];
function dayCardHTML(day, doy, wd) {
  const caps = DAYS[day] || [];
  return `<div class="abs" style="inset:0">${caps.map((k, i) => `<div class="abs" style="left:${328 * SPOTS[i][0]}px;top:${210 * SPOTS[i][1]}px;width:96px;height:96px;transform:rotate(${SPOTS[i][2]}deg)"><div class="abs" style="left:8px;top:8px;width:80px;height:80px">${stickerEl(k, 80, 80)}</div></div>`).join('')}
    <div class="abs" style="left:0;width:328px;top:22px;display:flex;flex-direction:column;align-items:center">
      <span class="mono" style="font-size:15px;letter-spacing:2px">2026</span>
      <span class="mono" style="font-size:12px;letter-spacing:.6px">OCTOBER</span>
      <span class="mono" style="font-size:10px;letter-spacing:.6px;color:var(--soft)">${doy}/365 ${wd}</span>
      <span class="mono" style="font-size:64px;font-weight:500;line-height:70px">${day}</span></div>
    ${caps.length ? `<span class="abs mono" style="left:${164 + 64 - 8}px;top:12px;font-size:12px;color:var(--soft)">×${caps.length}</span>` : ''}</div>`;
}
let day11, day5;
function buildCal() {
  buildMonth();
  day11 = h(dayCardHTML(11, 284, 'SUN')); day5 = h(dayCardHTML(5, 278, 'MON'));
  $('dayCard').appendChild(day11); $('dayCard').appendChild(day5);
}

// ---------- board ----------
const BOARD = [
  { k: 'sparrow1', x: 22, y: 30, w: 140, r: -6, t: 33.05 },
  { k: 'ginkgo', x: 182, y: 26, w: 92, r: 9, t: 33.35 },
  { k: 'bulbul', x: 20, y: 196, w: 128, r: 5, t: 33.65 },
  { k: 'violet', x: 188, y: 178, w: 86, r: -5, t: 33.95 },
];
const boardEls = [];
let boardText, boardDate, boardStamp, penPath;
function buildBoard() {
  const sh = $('sheet');
  css(sh, { left: '24px', top: '92px', width: '312px', height: '416px' });
  sh.classList.add('grain'); sh.style.backgroundSize = '96px 96px';
  BOARD.forEach((b) => {
    const s = IMG[b.k]; const hh = b.w * s.h / s.w;
    const e = h(`<div class="abs" style="left:${b.x}px;top:${b.y}px;width:${b.w}px;height:${hh}px">${stickerEl(b.k, b.w, hh)}</div>`);
    sh.appendChild(e); boardEls.push({ e, b });
  });
  boardText = h('<div class="abs" style="left:150px;top:318px;font-size:18px;letter-spacing:1px">孤山，早上八点</div>');
  boardDate = h('<div class="abs mono" style="left:151px;top:346px;font-size:11px;letter-spacing:.6px;color:var(--soft)">2026.10.11</div>');
  boardStamp = h(`<div class="abs" style="right:12px;bottom:12px;width:46px;height:46px">${collectStampSVG(46)}</div>`);
  sh.appendChild(boardText); sh.appendChild(boardDate); sh.appendChild(boardStamp);
  const pen = $('pen'); pen.setAttribute('width', 312); pen.setAttribute('height', 416);
  pen.innerHTML = '<path id="penPath" d="M150 372 C 175 364, 196 380, 220 370 S 262 362, 282 372" fill="none" stroke="#22392F" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/>';
  penPath = $('penPath');
  sh.appendChild(pen);
}

// ---------- captions ----------
const CAPS = [
  [3.45, 10.05, '01', '拍下它', '自动抠出主体，做成一张贴纸'],
  [10.15, 13.7, '02', '认出它', '名字、学名和简介自动写好'],
  [13.8, 17.55, '03', '攒成图鉴', '按物种归档，按类别筛选'],
  [17.65, 24.5, '04', '每次遇见都记着', '物种时间线：哪天、在哪、第几次'],
  [24.6, 29.5, '05', '回看足迹', '贴纸就是地图上的标记'],
  [29.6, 32.55, '06', '按日子翻', '每天收集了什么，一眼看到'],
  [32.65, 36.95, '07', '拼一页手帐', '贴纸、文字、画笔，随手摆'],
  [37.05, 40.5, '08', '分享这次遇见', '生成一张标本卡片'],
];
const capEls = CAPS.map((c) => {
  const e = h(`<div class="cap"><div class="idx">${c[2]}</div><div class="title">${c[3]}</div><div class="sub">${c[4]}</div></div>`);
  $('cap').appendChild(e); return e;
});

// ---------- finding canvas ----------
const find = { ready: false };
function tinted(img, color) {
  const c = document.createElement('canvas'); c.width = img.naturalWidth; c.height = img.naturalHeight;
  const g = c.getContext('2d'); g.drawImage(img, 0, 0); g.globalCompositeOperation = 'source-in'; g.fillStyle = color; g.fillRect(0, 0, c.width, c.height);
  return c;
}
function setupFind(imgs) {
  const cv = $('findCanvas');
  const pw = 320, ph = 320 * imgs.photo.naturalHeight / imgs.photo.naturalWidth;
  cv.width = Math.round(pw * 3.8); cv.height = Math.round(ph * 3.8);
  css(cv, { height: ph + 'px', top: (24 + (651.5 - ph) / 2) + 'px' });
  find.cv = cv; find.g = cv.getContext('2d'); find.ph = ph; find.photo = imgs.photo;
  find.edges = tinted(imgs.edges, '#FBFBF7');
  find.layers = [imgs.rail, imgs.union, imgs.bird];
  find.white = find.layers.map((l) => tinted(l, '#FBFBF7'));
  find.rust = find.layers.map((l) => tinted(l, RUST));
  const chip = $('findChip');
  css(chip, { top: (24 + 651.5 - 28 - 36) + 'px' });
  find.ready = true;
}
function drawFind(t) { // t = seconds since the Finding stage began
  const { g, cv } = find; const W = cv.width, H = cv.height, k = W / 320;
  const intro = FOS(seg(t, 0, 0.5));
  g.setTransform(1, 0, 0, 1, 0, 0); g.clearRect(0, 0, W, H);
  const sc = 0.96 + 0.04 * intro;
  g.translate(W / 2, H / 2); g.scale(sc, sc); g.translate(-W / 2, -H / 2);
  g.drawImage(find.photo, 0, 0, W, H);
  g.fillStyle = `rgba(0,0,0,${0.32 * intro})`; g.fillRect(0, 0, W, H);
  const L0 = 1.45; // layers arrive
  const fade = 1 - seg(t, L0, L0 + 0.3);
  if (fade > 0) {
    const ph = (t % 1.7) / 1.7; const sweep = -0.08 + 1.16 * FOS(ph);
    const y = sweep * H, band = 70 * k;
    g.save(); g.beginPath(); g.rect(0, 0, W, clamp(y, 0, H)); g.clip();
    g.globalAlpha = 0.16 * fade; g.drawImage(find.edges, 0, 0, W, H); g.restore();
    for (let i = 0; i < 4; i++) {
      const top = clamp(y - band * (i + 1) / 4, 0, H), bot = clamp(y - band * i / 4, 0, H);
      if (bot <= top) continue;
      g.save(); g.beginPath(); g.rect(0, top, W, bot - top); g.clip();
      g.globalAlpha = (0.9 - i * 0.2) * fade; g.drawImage(find.edges, 0, 0, W, H); g.restore();
    }
    const gr = g.createLinearGradient(0, y - band, 0, y);
    gr.addColorStop(0, 'rgba(251,251,247,0)'); gr.addColorStop(1, `rgba(251,251,247,${0.12 * fade})`);
    g.fillStyle = gr; g.fillRect(0, y - band, W, band);
    g.globalAlpha = 0.95 * fade; g.fillStyle = RUST; g.fillRect(0, y - 0.75 * k, W, 1.5 * k); g.globalAlpha = 1;
  }
  const chosen = 2, n = 3, settle = L0 + 0.26 * n + 0.08;
  for (let i = 0; i < n; i++) {
    const s0 = L0 + 0.26 * i;
    let a = seg(t, s0, s0 + 0.22);
    if (i !== chosen && t > s0 + 0.22) a = lerp(1, 0.35, seg(t, s0 + 0.22, s0 + 0.48));
    if (t > settle) a = lerp(a, i === chosen ? 1 : 0, seg(t, settle, settle + 0.3));
    if (a <= 0) continue;
    g.globalAlpha = i === chosen ? a : a * 0.7;
    g.drawImage(i === chosen ? find.rust[i] : find.white[i], 0, 0, W, H);
  }
  g.globalAlpha = 1;
  $('findChip').style.opacity = 1 - seg(t, L0, L0 + 0.25);
  const cw = $('findChip').offsetWidth; $('findChip').style.left = (180 - cw / 2) + 'px';
}

// ---------- burst ----------
function drawBurst(p) {
  const cv = $('burst'), g = cv.getContext('2d'); g.clearRect(0, 0, cv.width, cv.height);
  if (p <= 0 || p >= 1) return;
  const k = 2, W = 360 * k, H = 651.5 * k, c = [W / 2, H / 2], m = Math.min(W, H), base = m * 0.28;
  for (let i = 0; i < 7; i++) {
    const ang = (i / 7) * Math.PI * 2 + 0.4, dist = base + m * 0.16 * p;
    const r = (i % 2 === 0 ? 3 : 2) * k * (1 - p * 0.5);
    g.globalAlpha = 1 - p; g.fillStyle = i % 3 === 0 ? RUST : '#fff';
    g.beginPath(); g.arc(c[0] + Math.cos(ang) * dist, c[1] + Math.sin(ang) * dist, r, 0, Math.PI * 2); g.fill();
  }
  g.globalAlpha = 1;
}

// holographic sheen (Common.kt): band 0.6w, alpha intensity*0.5, phase -1 → 2
function holo(el, t, period, intensity) {
  const p = t / period; if (t < 0 || p > 1) { el.style.background = 'none'; return; }
  const phase = -1 + 3 * p, a = Math.min(0.3, intensity * 0.5);
  const x = phase * 100;
  el.style.background = `linear-gradient(115deg, rgba(255,255,255,0) ${x - 60}%, rgba(255,255,255,${a}) ${x - 30}%, rgba(255,255,255,0) ${x}%)`;
}

// tap indicator, in screen dp
function tapAt(t, list) {
  const e = $('tap'); let shown = false;
  for (const [t0, x, y, hold = 0] of list) {
    if (t >= t0 - 0.18 && t <= t0 + hold + 0.32) {
      const inA = seg(t, t0 - 0.18, t0), out = seg(t, t0 + hold + 0.05, t0 + hold + 0.32);
      const mv = typeof x === 'function' ? x(t) : [x, y];
      css(e, { left: mv[0] + 'px', top: mv[1] + 'px', opacity: inA * (1 - out), transform: `scale(${0.75 + 0.25 * inA - 0.1 * (t >= t0 && t < t0 + 0.1 ? 1 : 0)})` });
      shown = true; break;
    }
  }
  if (!shown) e.style.opacity = 0;
}

// ======================= render =======================
const SCR = ['sLive', 'sFind', 'sStick', 'sSaved', 'sColl', 'sDetail', 'sMap', 'sCal', 'sBoard'];
function show(map) { SCR.forEach((id) => { const o = map[id] || 0; const e = $(id); e.style.opacity = o; e.style.display = o > 0 ? 'block' : 'none'; }); }
function xfade(t, t0, d = 0.25) { return seg(t, t0, t0 + d); }

window.render = function render(t) {
  // ---- hook ----
  const hook = $('hook');
  if (t < 3.75) {
    hook.style.display = 'block';
    const zA = 1.10 - 0.08 * seg(t, 0, 2.0);
    css($('hookA'), { transform: `scale(${zA})` });
    const bIn = seg(t, 1.55, 1.95);
    css($('hookB'), { opacity: bIn, transform: `scale(${1.06 - 0.04 * seg(t, 1.55, 3.0)})` });
    css($('hookCap1'), { opacity: seg(t, 0.25, 0.6) * (1 - seg(t, 1.45, 1.7)), transform: `translateY(${20 * (1 - EIO(seg(t, 0.25, 0.6)))}px)` });
    css($('hookCap2'), { opacity: seg(t, 1.85, 2.2) * (1 - seg(t, 2.85, 3.05)), transform: `translateY(${20 * (1 - EIO(seg(t, 1.85, 2.2)))}px)` });
    $('hookShade').style.opacity = 1 - seg(t, 2.9, 3.2);
    const m = FOS(seg(t, 3.0, 3.7));
    const R = { x: 198 + 12 * 1.9, y: 352 + 66 * 1.9, w: 336 * 1.9, h: 493.5 * 1.9 };
    css(hook, { left: lerp(0, R.x, m) + 'px', top: lerp(0, R.y, m) + 'px', width: lerp(1080, R.w, m) + 'px', height: lerp(1920, R.h, m) + 'px', borderRadius: lerp(0, 4, m) + 'px' });
    if (m > 0) css($('hookB'), { transform: `scale(${lerp(1.02, 1.0, m)})` });
    hook.style.opacity = t > 3.6 ? 1 - seg(t, 3.6, 3.75) : 1;
  } else hook.style.display = 'none';

  // ---- phone in/out ----
  const pin = seg(t, 2.75, 3.25), pe = FOS(pin);
  const shareP = FOS(seg(t, 37.0, 37.6));
  css($('phone'), { opacity: pin * (1 - seg(t, 37.15, 37.55)), transform: `translateY(${(1 - pe) * 90 + shareP * 60}px) scale(${1 - 0.06 * shareP})` });

  // ---- captions ----
  CAPS.forEach((c, i) => {
    const a = seg(t, c[0], c[0] + 0.35) * (1 - seg(t, c[1] - 0.25, c[1]));
    css(capEls[i], { opacity: a, transform: `translateY(${16 * (1 - EIO(seg(t, c[0], c[0] + 0.35)))}px)` });
  });

  // ---- screens ----
  const vis = {};
  const st = $('status'); st.style.color = '#22392F';
  let tab = 'cam', navShow = 1, camLift = 1;
  if (t < 5.35) vis.sLive = 1;
  else if (t < 8.1) { vis.sLive = 1 - seg(t, 5.35, 5.55); vis.sFind = xfade(t, 5.35); }
  else if (t < 10.05) { vis.sFind = 1 - seg(t, 8.1, 8.3); vis.sStick = xfade(t, 8.1); }
  else if (t < 13.75) { vis.sStick = 1 - seg(t, 10.05, 10.25); vis.sSaved = xfade(t, 10.05); }
  else if (t < 17.2) { vis.sSaved = 1 - seg(t, 13.75, 13.95); vis.sColl = xfade(t, 13.75, 0.3); tab = 'coll'; }
  else if (t < 24.5) { vis.sColl = 1 - seg(t, 17.2, 17.45); vis.sDetail = xfade(t, 17.2, 0.35); tab = 'coll'; navShow = 1 - seg(t, 17.2, 17.45); }
  else if (t < 29.55) { vis.sDetail = 1 - seg(t, 24.5, 24.75); vis.sMap = xfade(t, 24.5, 0.35); tab = 'map'; navShow = seg(t, 24.5, 24.8); }
  else if (t < 32.6) { vis.sMap = 1 - seg(t, 29.55, 29.75); vis.sCal = xfade(t, 29.55, 0.3); tab = 'cal'; }
  else { vis.sCal = 1 - seg(t, 32.6, 32.8); vis.sBoard = xfade(t, 32.6, 0.3); tab = 'board'; }
  if (t < 5.4) st.style.color = '#fff';
  show(vis);

  // nav bar
  const nav = $('nav');
  css(nav, { transform: `translateY(${(1 - navShow) * 104}px)`, opacity: navShow });
  document.querySelectorAll('.navitem').forEach((n) => n.classList.toggle('on', n.dataset.tab === tab));
  const lift = tab === 'cam' ? -26 : -20;
  const liftNow = t >= 13.6 && t < 14.2 ? lerp(-26, -20, spring(t - 13.6, 0.7, 1500)) : lift;
  $('shutterNav').style.bottom = (16 - liftNow) + 'px';

  // ---- live camera ----
  if (vis.sLive) {
    const drift = Math.sin(t * 1.3) * 2, drift2 = Math.cos(t * 1.1) * 1.5;
    css($('vfimg'), { transform: `scale(1.03) translate(${drift}px, ${drift2}px)` });
    const fr = seg(t, 4.0, 4.65);
    const f = $('focus');
    if (t >= 4.0 && t < 4.65) {
      const r = 34 - 10 * fr;
      css(f, { left: 190 - r + 'px', top: 230 - r + 'px', width: 2 * r + 'px', height: 2 * r + 'px', opacity: 1 - fr * 0.7 });
    } else f.style.opacity = 0;
    let press = 1;
    if (t >= 5.0) press = t < 5.07 ? lerp(1, 0.86, seg(t, 5.0, 5.07)) : lerp(0.86, 1, spring(t - 5.07, 0.5, 1500));
    $('shutter').style.transform = `scale(${press})`;
    $('flash').style.opacity = t >= 5.07 ? 0.9 * (1 - seg(t, 5.07, 5.42)) : 0;
  }
  // ---- finding ----
  if (vis.sFind && find.ready) drawFind(t - 5.35);

  // ---- sticker reveal ----
  if (vis.sStick) {
    const ts = t - 8.1;
    const sep = FOS(seg(ts, 0, 0.42));
    css($('stBg'), { filter: `blur(${14 * sep}px)` });
    $('stWash').style.opacity = 0.6 * sep;
    drawBurst(LOS(seg(ts, 0, 0.75)) * (ts < 0.75 ? 1 : 0) || (ts > 0 && ts < 0.75 ? 0.001 : 0));
    let pop = lerp(0.3, 1, spring(ts, 0.45, 400));
    const s = IMG.sparrow1, W = 360 * 0.78, Hh = W * s.h / s.w;
    // drag up to peel (fingers down at 9.2, release 9.6)
    const d0 = 9.2 - 8.1, d1 = 9.6 - 8.1;
    let lift = 1, ox = 0, oy = 0, fade = 1;
    if (ts >= d0) lift = lerp(1, 1.08, spring(ts - d0, 1, 1500));
    if (ts >= d0) { const k = EIO(seg(ts, d0 + 0.05, d1)); ox = 14 * k; oy = -150 * k; }
    if (ts >= d1) {
      const k = seg(ts, d1, d1 + 0.42);
      ox = lerp(14, 21, k); oy = lerp(-150, -651.5, k); lift = lerp(1.08, 0.35, k); fade = 1 - k;
    }
    const sc = pop * lift;
    css($('stSticker'), { left: (180 - W / 2) + 'px', top: (325.75 - Hh / 2) + 'px', width: W + 'px', height: Hh + 'px',
      transform: `translate(${ox}px, ${oy}px) perspective(${12 * 1.9 * 100}px) rotateX(${clamp(-oy / 30, -18, 18)}deg) rotateZ(${ox / 22}deg) scale(${sc})`, opacity: fade });
    $('stHolo').style.webkitMaskImage = `url(${stickerURL('sparrow1')})`;
    holo($('stHolo'), ts, 2.6, 0.4);
    if (ts >= d0) holo($('stHolo'), ts - d0, 2.6, 0.4);
  }
  // ---- saved ----
  if (vis.sSaved) {
    const ts = t - 10.05;
    const col = $('savedCol');
    const hh = col.offsetHeight; const en = spring(ts, 0.8, 200);
    css(col, { top: (24 + (651.5 - hh) / 2) + 'px', opacity: clamp(en), transform: `translateY(${(1 - en) * 60 / 1.9}px)` });
    $('savedHolo').style.webkitMaskImage = `url(${stickerURL('sparrow1')})`;
    holo($('savedHolo'), ts, 3.2, 0.3);
    const done = ts > 1.25;
    $('savedWait').style.display = done ? 'none' : 'flex';
    $('savedDone').style.display = done ? 'block' : 'none';
    $('savedDone').style.opacity = seg(ts, 1.25, 1.75);
    $('spin').style.transform = `rotate(${ts * 400}deg)`;
  }
  // ---- collection ----
  if (vis.sColl) {
    const tc = t - 13.75;
    const ft = 15.45 - 13.75;
    const birdsOnly = tc >= ft;
    chips($('collChips'), ['全部', '鸟类', '花卉', '植物', '昆虫', '＋ 分类'], birdsOnly ? '鸟类' : '全部');
    ORDER.forEach((k, i) => {
      const e = cells[k];
      const ap = lerp(0.6, 1, spring(tc, 0.5, 400));
      e.querySelector('.art').style.transform = `scale(${ap})`;
      let p = cellPos(i), o = 1, sc = 1;
      const bi = BIRDS.indexOf(k);
      if (birdsOnly) {
        if (bi < 0) { const k2 = seg(tc, ft, ft + 0.2); o = 1 - k2; sc = 1 - 0.15 * k2; }
        else { const q = cellPos(bi), m = spring(tc - ft - 0.08, 1, 400); p = { x: lerp(p.x, q.x, m), y: lerp(p.y, q.y, m) }; }
      }
      css(e, { left: p.x + 'px', top: p.y + 'px', opacity: o, transform: `scale(${sc})`, display: o > 0 ? 'block' : 'none' });
    });
    $('collInner').style.transform = 'none';
  }
  // ---- detail ----
  if (vis.sDetail) {
    const td = t - 17.2;
    const useB = t >= 22.4;
    const cur = useB ? detB : detA, other = useB ? detA : detB;
    const xo = seg(t, 22.4, 22.75);
    css(detA, { display: (!useB || xo < 1) ? 'block' : 'none', opacity: useB ? 1 - xo : 1 });
    css(detB, { display: useB ? 'block' : 'none', opacity: xo });
    const tlA = detA.querySelector('.tl'), maxA = tlA.offsetTop + tlA.offsetHeight + 16 - (756 - 16);
    const scA = maxA * FOS(seg(t, 18.9, 20.1));
    detA.style.transform = `translateY(${-scA}px)`;
    const tlB = detB.querySelector('.tl'), maxB = tlB.offsetTop + tlB.offsetHeight + 16 - (756 - 16);
    detB.style.transform = `translateY(${-maxB * FOS(seg(t, 23.3, 24.1))}px)`;
    holo(detA.querySelector('.holo'), td - 0.2, 3.0, 0.32);
    holo(detB.querySelector('.holo'), t - 22.6, 3.0, 0.32);
    // tap target for row 4 of detA's timeline
    const rows = detA.querySelectorAll('.trow');
    const r4 = rows[3]; detail.row4 = 24 + tlA.offsetTop + r4.offsetTop + 30 - scA;
  }
  // ---- map ----
  if (vis.sMap) {
    const tm = t - 24.5;
    const v = mapView(lerp(0.62, 0.75, FOS(seg(tm, 0.15, 1.15))));
    css($('mapTiles'), { transform: `translate(${v.left}px, ${v.top}px) scale(${v.s})` });
    MARKS.forEach(([k, x, y], i) => {
      const { e, f } = markerEls[i];
      const px = v.left + (x / 2) * v.s, py = v.top + (y / 2) * v.s;
      const p = spring(tm - 0.5 - i * 0.07, 0.55, 400);
      css(e, { left: px - f.w / 2 + 'px', top: py - f.h / 2 + 'px', transform: `scale(${Math.max(0, p)})`, opacity: clamp(p * 3) });
    });
    const sh = FOS(seg(tm, 2.65, 2.95));
    $('mapScrim').style.opacity = 0.32 * sh;
    const sheet = $('placeSheet'); const shh = sheet.offsetHeight;
    css(sheet, { top: (780 - shh * sh) + 'px' });
  }
  // ---- calendar ----
  if (vis.sCal) {
    const sel = t >= 31.05 ? 5 : 11;
    const x = seg(t, 31.05, 31.3);
    css(day11, { opacity: t >= 31.05 ? 1 - x : 1 });
    css(day5, { opacity: t >= 31.05 ? x : 0 });
    document.querySelectorAll('#month .dc').forEach((c) => {
      const d = +c.dataset.d; c.style.boxShadow = d === sel ? 'inset 0 0 0 1px rgba(34,57,47,.6)' : 'none';
    });
  }
  // ---- board ----
  if (vis.sBoard) {
    boardEls.forEach(({ e, b }) => {
      const p = spring(t - b.t, 0.6, 400);
      css(e, { opacity: clamp((t - b.t) / 0.15), transform: `translateY(${(1 - p) * -18}px) rotate(${b.r}deg) scale(${lerp(1.3, 1, p)})` });
    });
    boardText.style.opacity = seg(t, 34.55, 34.85);
    boardDate.style.opacity = seg(t, 34.8, 35.1);
    const L = penPath.getTotalLength(); const pk = EIO(seg(t, 35.2, 35.95));
    css(penPath, { strokeDasharray: `${L}`, strokeDashoffset: `${L * (1 - pk)}` });
    const sp = spring(t - 36.2, 0.6, 400);
    css(boardStamp, { opacity: clamp((t - 36.2) / 0.15), transform: `scale(${lerp(1.4, 1, sp)})` });
  }

  // ---- taps (screen dp) ----
  tapAt(t, [
    [4.0, 190 + 12, 230 + 66], [5.0, 180, 622.5],
    [9.2, (tt) => [180 + 14 * EIO(seg(tt, 9.25, 9.6)), 300 - 150 * EIO(seg(tt, 9.25, 9.6))], 0, 0.4],
    [13.55, 38.5, 733.5], [15.45, 89, 184], [16.9, 96, 305],
    [22.1, 200, detail.row4 || 600], [27.0, 16 + 6 + mapPt(305, 1288)[0], 134 + 6 + mapPt(305, 1288)[1]],
    [30.95, 16 + 6 + (316 / 7) * 4.5, 392 + 37 + (316 / 7 / 0.9) * 0.5],
  ]);

  // ---- share ----
  const sIn = seg(t, 37.15, 37.75), sp = spring(t - 37.15, 0.85, 200);
  if (t >= 37.15 && t < 40.9) {
    const s = 0.78, x = (1080 - 1080 * s) / 2, y = 372;
    css($('share'), { opacity: clamp(sIn * 2) * (1 - seg(t, 40.5, 40.9)), transform: `translate(${x}px, ${y + (1 - sp) * 260}px) scale(${s})`, boxShadow: '0 30px 70px rgba(30,50,40,.20)' });
    const f = fitBox('sparrow1', 740 * 0.86, 740 * 0.86);
    css($('shareSticker'), { left: (510 - f.w / 2) + 'px', top: (524 - f.h / 2) + 'px', width: f.w + 'px', height: f.h + 'px', transform: 'rotate(-4deg)' });
  } else $('share').style.opacity = 0;

  // ---- end ----
  const e = seg(t, 40.6, 41.1);
  $('end').style.opacity = e;
  if (e > 0) {
    const lg = spring(t - 40.8, 0.6, 200);
    css($('endLogo'), { opacity: clamp((t - 40.8) / 0.3), transform: `scale(${lerp(0.85, 1, lg)}) rotate(${lerp(-8, 0, lg)}deg)` });
    css($('endName'), { opacity: seg(t, 41.15, 41.55), transform: `translateY(${16 * (1 - EIO(seg(t, 41.15, 41.55)))}px)` });
    css($('endTag'), { opacity: seg(t, 41.5, 41.9) });
    css($('endRule'), { opacity: seg(t, 41.7, 42.0), transform: `scaleX(${EIO(seg(t, 41.7, 42.1))})` });
    css($('endMono'), { opacity: seg(t, 41.9, 42.3) });
    css($('endCredit'), { opacity: 0.8 * seg(t, 42.2, 42.6) });
  }
};
const detail = {};
function mapPt(x, y) { const v = mapView(0.75); return [v.left + (x / 2) * v.s, v.top + (y / 2) * v.s]; }

// ---------- boot ----------
function load(src) { return new Promise((res, rej) => { const i = new Image(); i.onload = () => res(i); i.onerror = rej; i.src = src; }); }
window.ready = (async () => {
  const keys = ['sparrow1', 'sparrow2', 'sparrow3', 'sparrow4', 'bulbul', 'magpie', 'violet', 'ginkgo', 'ladybird', 'dandelion', 'butterfly'];
  await Promise.all(keys.map(async (k) => { const i = await load(stickerURL(k)); IMG[k] = { w: i.naturalWidth, h: i.naturalHeight }; }));
  const [photo, edges, rail, union, bird] = await Promise.all(['img/sparrow1.jpg', 'img/sparrow1_edges.png', 'img/sparrow1_layer_rail.png', 'img/sparrow1_layer_union.png', 'img/sparrow1_outline.png'].map(load));
  await Promise.all(['img/scene.jpg', 'img/map.png', 'shiye_mark.svg'].map(load));
  await document.fonts.load('500 20px "Noto Sans SC"'); await document.fonts.load('700 20px "Noto Sans SC"'); await document.fonts.load('400 20px "Noto Sans SC"');
  await document.fonts.load('400 20px "DM Mono"'); await document.fonts.load('500 20px "DM Mono"');
  buildGrid(); buildDetail(); buildMap(); buildCal(); buildBoard();
  setupFind({ photo, edges, rail, union, bird });
  await document.fonts.ready;
  window.render(0);
  return true;
})();
