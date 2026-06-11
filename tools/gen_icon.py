# -*- coding: utf-8 -*-
"""
券管家 Coupy — Logo #2（折扣・%券）icon 產生器
設計與 logo_concepts.html 完全一致：
  - 票券 50x36（0-100 設計座標，中心 50,50），圓角 7，左右缺口 r4
  - % 符號（sc=1.3），對角線 + 兩個圈
前景圖：白票券，% 與缺口為「鏤空透明」，疊在藍底上 -> 藍色 % 白票券
"""
import os
from PIL import Image, ImageDraw

BLUE = (0x4A, 0x89, 0xDC, 255)
SS = 4  # supersample

# ---- 設計座標（0-100，中心 50,50）----
TICKET = (25, 32, 75, 68)      # x0,y0,x1,y1  -> 50 寬 x 36 高
TICKET_R = 7
NOTCH = [(25, 50), (75, 50)]   # 圓心
NOTCH_R = 4
# % (sc = 1.3)
SC = 1.3
PCT_LINE = ((50 + 7*SC, 50 - 7*SC), (50 - 7*SC, 50 + 7*SC))  # 對角
PCT_LW = 3.4 * SC
PCT_RINGS = [(50 - 6*SC, 50 - 6*SC), (50 + 6*SC, 50 + 6*SC)]
PCT_RING_R = 3.3 * SC
PCT_RING_LW = 2.1 * SC


def draw_mask(W, H, ox, oy, sc):
    """回傳 L 遮罩：255=白票券實體，0=鏤空(缺口/%）。"""
    m = Image.new('L', (W*SS, H*SS), 0)
    d = ImageDraw.Draw(m)

    def X(v): return (ox + v*sc) * SS
    def Y(v): return (oy + v*sc) * SS

    # 票券本體
    d.rounded_rectangle([X(TICKET[0]), Y(TICKET[1]), X(TICKET[2]), Y(TICKET[3])],
                        radius=TICKET_R*sc*SS, fill=255)
    # 缺口鏤空
    for (cx, cy) in NOTCH:
        d.ellipse([X(cx-NOTCH_R), Y(cy-NOTCH_R), X(cx+NOTCH_R), Y(cy+NOTCH_R)], fill=0)
    # % 對角線鏤空（含圓頭）
    (ax, ay), (bx, by) = PCT_LINE
    lw = PCT_LW * sc * SS
    d.line([X(ax), Y(ay), X(bx), Y(by)], fill=0, width=int(round(lw)))
    cap = PCT_LW/2 * sc * SS
    for (px, py) in (PCT_LINE[0], PCT_LINE[1]):
        d.ellipse([X(px)-cap, Y(py)-cap, X(px)+cap, Y(py)+cap], fill=0)
    # % 兩個圈鏤空（環狀）
    for (cx, cy) in PCT_RINGS:
        d.ellipse([X(cx-PCT_RING_R), Y(cy-PCT_RING_R), X(cx+PCT_RING_R), Y(cy+PCT_RING_R)],
                  outline=0, width=int(round(PCT_RING_LW*sc*SS)))

    return m.resize((W, H), Image.LANCZOS)


def make_foreground(P):
    """透明前景：白票券 + 鏤空。P = 像素邊長。設計填滿整張(0-100 -> 0-P)。"""
    sc = P / 100.0
    mask = draw_mask(P, P, 0, 0, sc)
    white = Image.new('L', (P, P), 255)
    return Image.merge('RGBA', (white, white, white, mask))


def make_store(P=512):
    """商店圖：藍底 + 放大的票券構圖（票券約佔 74%）。"""
    sc = P * 0.74 / 50.0           # 票券 50 設計單位 -> 74% 寬
    ox = P/2 - 50*sc               # 讓設計中心 50 對到 P/2（像素 = ox + v*sc）
    oy = ox
    mask = draw_mask(P, P, ox, oy, sc)
    white = Image.new('L', (P, P), 255)
    fg = Image.merge('RGBA', (white, white, white, mask))
    bg = Image.new('RGBA', (P, P), BLUE)
    out = Image.alpha_composite(bg, fg)
    return out.convert('RGB')


HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, 'android', 'app', 'src', 'main', 'res')

DENS = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}

for dens, px in DENS.items():
    fg = make_foreground(px)
    out_dir = os.path.join(RES, f'mipmap-{dens}')
    fg.save(os.path.join(out_dir, 'ic_launcher_foreground.png'))
    print(f'foreground {dens}: {px}px -> {out_dir}')

store = make_store(512)
store_path = os.path.join(ROOT, 'coupy_play_store_512.png')
store.save(store_path)
print(f'store icon: {store_path}')
print('done')
