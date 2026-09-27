import math
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(ROOT, "..", "..", "..", "src", "main", "resources", "assets", "hp_end_expansion", "textures")
EFFECT = os.path.join(ASSETS, "effect")
PARTICLE = os.path.join(ASSETS, "particle")
os.makedirs(EFFECT, exist_ok=True)
os.makedirs(PARTICLE, exist_ok=True)

# 色板：沿用模型的裂隙紫与甲壳暗色
CORE = (232, 201, 255)
BRIGHT = (178, 109, 227)
MID = (120, 67, 174)
DEEP = (60, 33, 87)
DIM = (30, 16, 48)
VOID = (11, 8, 18)
SHELL = (35, 29, 48)
SHELL_L = (64, 52, 76)


def hex_img(w, h):
    return Image.new("RGBA", (w, h), (0, 0, 0, 0))


# 弧光条带：u 沿弧长，v 从外刃到内侧，黑色即加法混合下的透明
def make_arc():
    w, h = 64, 16
    im = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    ramp = [CORE, CORE, BRIGHT, BRIGHT, MID, MID, DEEP, DEEP, DIM, DIM]
    for x in range(w):
        t = (x + 0.5) / w
        width = int(round(math.sin(math.pi * t) ** 0.7 * 15))
        head = 1 if t > 0.55 else 0
        for y in range(h):
            if y >= width:
                continue
            idx = int(y * len(ramp) / max(width, 1))
            idx = min(idx + (0 if t > 0.3 else 1), len(ramp) - 1)
            idx = max(idx - head, 0)
            c = ramp[idx]
            im.putpixel((x, y), c + (255,))
    for x in range(4, w - 4, 7):
        y = 1 + (x * 5) % 3
        if im.getpixel((x, y))[:3] != (0, 0, 0):
            im.putpixel((x, y), (255, 240, 255, 255))
    im.save(os.path.join(EFFECT, "rift_arc.png"))


# 裂隙本体：锯齿透镜轮廓，内部为虚空与暗紫旋带
def make_portal():
    w, h = 32, 64
    rnd = random.Random(7)
    body = hex_img(w, h)
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    jag = [rnd.choice([-1, 0, 0, 1]) for _ in range(h)]
    for y in range(h):
        t = (y + 0.5) / h
        half = (math.sin(math.pi * t) ** 0.9) * 11 + jag[y]
        half = max(half, 0)
        for x in range(w):
            d = abs(x + 0.5 - w / 2)
            if d > half:
                continue
            edge = half - d
            if edge < 1.2:
                c = BRIGHT
            elif edge < 2.2:
                c = MID
            elif edge < 3.2:
                c = DEEP
            else:
                band = int((y * 0.35 + x * 0.6)) % 7
                if band == 0:
                    c = SHELL
                elif band == 3:
                    c = DIM
                else:
                    c = VOID
            body.putpixel((x, y), c + (255,))
    for _ in range(14):
        x = rnd.randint(11, 20)
        y = rnd.randint(10, 53)
        if body.getpixel((x, y))[:3] in (VOID, DIM, SHELL):
            body.putpixel((x, y), CORE + (255,))
    for y in range(h):
        t = (y + 0.5) / h
        half = (math.sin(math.pi * t) ** 0.9) * 11 + jag[y]
        for x in range(w):
            d = abs(abs(x + 0.5 - w / 2) - half)
            if half <= 0:
                continue
            if d < 1.0:
                c = CORE
            elif d < 2.0:
                c = BRIGHT
            elif d < 3.5:
                c = MID
            elif d < 5.0:
                c = DEEP
            else:
                continue
            glow.putpixel((x, y), c + (255,))
    body.save(os.path.join(EFFECT, "rift_portal.png"))
    glow.save(os.path.join(EFFECT, "rift_portal_glow.png"))


# 地裂条带：u 横跨宽度，v 可平铺，带断续高光
def make_crack():
    w, h = 16, 32
    rnd = random.Random(11)
    im = hex_img(w, h)
    for y in range(h):
        wob = rnd.choice([-1, 0, 0, 1])
        for x in range(w):
            d = abs(x + 0.5 - w / 2 - wob * 0.5)
            if d < 1.0:
                c = CORE if (y % 9) not in (4, 5) else BRIGHT
            elif d < 2.2:
                c = BRIGHT
            elif d < 3.6:
                c = MID
            elif d < 5.2:
                c = DEEP
            elif d < 7.0:
                c = DIM
            else:
                continue
            im.putpixel((x, y), c + (255,))
    im.save(os.path.join(EFFECT, "rift_crack.png"))


# 裂隙晶刺：尖三角，暗色晶体配单侧亮边
def make_spike():
    w, h = 16, 32
    im = hex_img(w, h)
    for y in range(h):
        t = y / (h - 1)
        half = t * 7.0 + 0.5
        for x in range(w):
            dx = x + 0.5 - w / 2
            if abs(dx) > half:
                continue
            if dx < -half + 1.2:
                c = CORE
            elif dx < -half + 2.4:
                c = BRIGHT
            elif dx > half - 1.2:
                c = SHELL
            elif abs(dx) < 1.0:
                c = MID
            else:
                c = DEEP if (x + y) % 5 else SHELL_L
            im.putpixel((x, y), c + (255,))
    for y in range(0, 3):
        im.putpixel((8, y), CORE + (255,))
    im.save(os.path.join(EFFECT, "rift_spike.png"))


# 碎片粒子：四种不同角度的小三角碎片
def make_shards():
    shapes = [
        [(1, 1), (2, 1), (3, 1), (4, 1), (5, 1), (2, 2), (3, 2), (4, 2), (3, 3), (4, 3), (3, 4), (4, 5)],
        [(1, 2), (2, 2), (2, 3), (3, 3), (3, 4), (4, 4), (4, 5), (5, 5), (5, 6), (2, 1), (6, 6)],
        [(3, 1), (3, 2), (4, 2), (2, 3), (3, 3), (4, 3), (2, 4), (3, 4), (4, 4), (5, 4), (3, 5), (4, 5)],
        [(5, 1), (4, 2), (5, 2), (3, 3), (4, 3), (5, 3), (2, 4), (3, 4), (4, 4), (1, 5), (2, 5)],
    ]
    for i, pts in enumerate(shapes):
        im = hex_img(8, 8)
        for j, (x, y) in enumerate(pts):
            if j == 0:
                c = CORE
            elif j < 3:
                c = BRIGHT
            elif j % 3 == 0:
                c = DEEP
            else:
                c = MID
            im.putpixel((x, y), c + (255,))
        im.save(os.path.join(PARTICLE, "rift_shard_%d.png" % i))


# 火花粒子：星形由大到小四帧
def make_sparks():
    frames = [
        {(3, 3): CORE, (4, 3): CORE, (3, 4): CORE, (4, 4): CORE,
         (3, 1): BRIGHT, (4, 1): BRIGHT, (3, 2): BRIGHT, (4, 2): BRIGHT,
         (3, 5): BRIGHT, (4, 5): BRIGHT, (3, 6): MID, (4, 6): MID,
         (1, 3): BRIGHT, (1, 4): BRIGHT, (2, 3): BRIGHT, (2, 4): BRIGHT,
         (5, 3): BRIGHT, (5, 4): BRIGHT, (6, 3): MID, (6, 4): MID,
         (3, 0): MID, (4, 7): DEEP, (0, 4): MID, (7, 3): DEEP},
        {(3, 3): CORE, (4, 3): CORE, (3, 4): CORE, (4, 4): CORE,
         (3, 2): BRIGHT, (4, 5): BRIGHT, (2, 4): BRIGHT, (5, 3): BRIGHT,
         (3, 1): MID, (4, 6): MID, (1, 4): MID, (6, 3): MID},
        {(3, 3): CORE, (4, 4): CORE, (3, 4): BRIGHT, (4, 3): BRIGHT,
         (3, 2): MID, (4, 5): MID, (2, 4): MID, (5, 3): MID},
        {(3, 3): BRIGHT, (4, 4): MID, (3, 4): MID, (4, 3): DEEP},
    ]
    for i, f in enumerate(frames):
        im = hex_img(8, 8)
        for (x, y), c in f.items():
            im.putpixel((x, y), c + (255,))
        im.save(os.path.join(PARTICLE, "rift_spark_%d.png" % i))


# 生物自发光层：只保留眼部与刃缘的裂隙紫像素
def make_glow_layer():
    src = Image.open(os.path.join(ASSETS, "entity", "rift_mantis.png")).convert("RGBA")
    keep = {(0x78, 0x43, 0xAE), (0xB2, 0x6D, 0xE3), (0xE8, 0xC9, 0xFF)}
    out = hex_img(*src.size)
    for y in range(src.height):
        for x in range(src.width):
            p = src.getpixel((x, y))
            if p[3] and p[:3] in keep:
                out.putpixel((x, y), p)
    out.save(os.path.join(ASSETS, "entity", "rift_mantis_glowmask.png"))


make_arc()
make_portal()
make_crack()
make_spike()
make_shards()
make_sparks()
make_glow_layer()
print("done")
