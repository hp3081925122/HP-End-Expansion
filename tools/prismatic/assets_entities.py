"""生成折光荒原原创实体：逐部位几何、独立面图案与确定性像素 UV。"""
from pathlib import Path
import json
import math
import hashlib
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/hp_end_expansion'
ART = ROOT / 'art/prismatic'
PALETTES = {
    'shell': ['514656', '8b8190', 'b9b0ac', 'd8ceb0', 'f2e7c6'],
    'bone': ['655568', '9e8b91', 'c7b7a6', 'e7d9bb', 'fff1d0'],
    'basalt': ['211c30', '302739', '46384e', '66536d', '917996'],
    'blue': ['2e394e', '40556a', '607d91', '87a5b1', 'bbd1cd'],
    'amber': ['773d36', 'ad633e', 'd68846', 'edac50', 'ffe5a0'],
    'violet': ['462a50', '744266', 'a35586', 'd56cb0', 'f9b5d4'],
    'wing': ['302739', '4a3c60', '71566f', '967b86', 'd5af9a'],
    'fur': ['554650', '8a7076', 'b79e97', 'dac6b0', 'eee2c7'],
    'chitin': ['262737', '384651', '536572', '708895', '9bb0b5'],
    'crystal': ['3e465c', '627795', '8ba2b2', 'bcd1ce', 'e2ece0'],
}
PALETTES = {k: [tuple(bytes.fromhex(c)) + (255,) for c in v] for k, v in PALETTES.items()}


# 每一面使用独立矩形，纹样由材质、部位和面方向共同决定。
def paint_face(w, h, material, label, face):
    colors = PALETTES[material]
    im = Image.new('RGBA', (w, h), colors[2])
    p = im.load()
    salt = int.from_bytes(hashlib.sha256((label + face).encode()).digest()[:4], 'little')
    for y in range(h):
        for x in range(w):
            noise = (x * 37 + y * 19 + (x * y) * 11 + salt) % 29
            idx = 2 if noise > 4 else (1 if noise < 3 else 3)
            if y == 0 or x == 0:
                idx = 3
            if y == h - 1 or x == w - 1:
                idx = 1
            if material in ('shell', 'chitin', 'basalt') and h > 4:
                if (y + (x // 4) % 2) % max(4, h // 2) == 0:
                    idx = 0
                elif (y + (x // 4) % 2) % max(4, h // 2) == 1:
                    idx = 3
            if material in ('crystal', 'amber', 'violet'):
                if x == max(1, w // 3) or x + y == w:
                    idx = 4
                elif x > w * .65:
                    idx = min(idx, 1)
            if material == 'fur' and (x + (y // 3) * 2) % 5 == 0:
                idx = 1 if y % 3 else 4
            if material == 'wing':
                if x == w // 2 or y == h // 2 or abs((x / max(1, w - 1)) - (y / max(1, h - 1))) < .06:
                    idx = 4
                elif (x // 3 + y // 3) % 3 == 0:
                    idx = 1
            p[x, y] = colors[idx]
    d = ImageDraw.Draw(im)
    if w * h >= 12:
        d.point((min(w - 1, 1), 0), fill=colors[4])
        d.point((max(0, w - 2), h - 1), fill=colors[0])
    if material == 'blue' and w >= 5 and h >= 4:
        d.line([(w // 2, 1), (w // 2 - 1, h // 2), (w // 2 + 1, h - 2)], fill=colors[0])
    if ('head' in label or 'face' in label) and face == 'north' and w >= 4 and h >= 3:
        ey = max(1, h // 3)
        for ex in (1, w - 3):
            d.rectangle((ex, ey, ex + 1, min(h - 2, ey + 1)), fill=PALETTES['basalt'][0])
            d.point((ex, ey), fill=PALETTES['amber'][4])
        if h >= 6:
            d.line((w // 3, h - 2, 2 * w // 3, h - 2), fill=colors[0])
    if 'core' in label and min(w, h) >= 5:
        d.polygon([(w // 2, 1), (w - 2, h // 2), (w // 2, h - 2), (1, h // 2)], outline=colors[4])
    if label.startswith('wing_'):
        if face not in ('up', 'down'):
            im = Image.new('RGBA', (w, h))
        else:
            mask = Image.new('L', (w, h))
            md = ImageDraw.Draw(mask)
            md.polygon([(0, 1), (w - 4, 0), (w - 1, 2), (w - 2, h - 2), (w // 2, h - 1), (0, h - 2)], fill=255)
            im.putalpha(mask)
    return im


# 骨骼使用绝对模型坐标与明确轴点；辅助字段不会写入运行资源。
class Model:
    def __init__(self, name):
        self.name = name
        self.bones = [{'name': 'root', 'pivot': [0, 0, 0]}]
        self.roles = {}

    def bone(self, name, pivot, parent='body', role=None):
        bone = {'name': name, 'parent': parent, 'pivot': list(pivot), 'cubes': []}
        self.bones.append(bone)
        if role:
            self.roles.setdefault(role, []).append(name)
        return name

    def cube(self, bone, origin, size, material='shell', rotation=None, pivot=None, label=None):
        cube = {'origin': list(origin), 'size': list(size), '_material': material, '_label': label or bone}
        if rotation:
            cube['rotation'] = list(rotation)
            cube['pivot'] = list(pivot or origin)
        next(b for b in self.bones if b['name'] == bone)['cubes'].append(cube)
        return cube


# 碎晶虫：低矮六足的三片甲壳，头和两片触角不与背甲共用轮廓。
def shardling():
    m = Model('shardling')
    m.bone('body', [0, 4, 0], 'root')
    m.cube('body', [-4, 2, -4], [8, 3, 10], 'chitin')
    for i, z in enumerate([-3, 0, 3]):
        n = m.bone(f'shell_{i}', [0, 4, z], role='shell')
        m.cube(n, [-5 + .75 * (i == 2), 4, z], [10 - 1.5 * (i == 2), 2, 3 + .25 * (i == 2)], 'shell')
    m.bone('head', [0, 4, -4], role='head')
    m.cube('head', [-3, 2, -7], [6, 3, 3], 'blue')
    for s, side in [(-1, 'l'), (1, 'r')]:
        n = m.bone('antenna_' + side, [s * 2, 5, -6], 'head', 'antenna')
        m.cube(n, [s * 2 - .5, 5, -7.2], [1, 4, 1], 'amber', [0, 0, -s * 25], [s * 2, 5, -6])
        for i, z in enumerate([-3, 0, 3]):
            n = m.bone(f'leg_{side}{i}', [s * 3, 3, z], role='leg')
            m.cube(n, [(-7 if s < 0 else 3), 1, z], [4, 2, 1], 'chitin')
            m.cube(n, [(-7.25 if s < 0 else 5.75), 0, z - .25], [1.5, 2, 2.5], 'basalt')
    return m


# 棱兔：长后肢、前后错位的耳棱与紧贴身躯的短尾。
def prism_hare():
    m = Model('prism_hare')
    m.bone('body', [0, 6, 1], 'root')
    m.cube('body', [-3, 3, -2], [6, 7, 8], 'fur')
    m.bone('head', [0, 9, -2], role='head')
    m.cube('head', [-3.25, 7, -6], [6.5, 6, 5], 'bone')
    m.cube('head', [-2, 6.75, -7], [4, 2.25, 2], 'fur', label='muzzle')
    for s, side in [(-1, 'l'), (1, 'r')]:
        n = m.bone('ear_' + side, [s * 1.5, 12, -3], 'head', 'antenna')
        m.cube(n, [s * 1.5 - 1, 12, -4], [2, 8, 2], 'crystal', [0, 0, -s * 12], [s * 1.5, 12, -3])
        n = m.bone('fore_' + side, [s * 2, 5, -2], role='fore')
        m.cube(n, [s * 2 - .875, 0, -3], [1.75, 6, 2], 'fur')
        n = m.bone('hind_' + side, [s * 2.5, 6, 3], role='hind')
        m.cube(n, [s * 2.5 - 1.5, 1, 2], [3, 5, 4.25], 'fur')
        m.cube(n, [s * 2.5 - 1.625, 0, 0], [3.25, 2, 5], 'bone')
    m.bone('tail', [0, 6, 6], role='tail')
    m.cube('tail', [-1.5, 5, 6], [3, 3, 3], 'crystal', [20, 0, 0], [0, 6, 6])
    return m


# 片角羊：厚肩、垂头、两段弯片角，腿端和肩背材质在 UV 内绘制。
def facet_ram():
    m = Model('facet_ram')
    m.bone('body', [0, 13, 0], 'root')
    m.cube('body', [-6, 8, -7], [12, 10, 16], 'fur')
    m.cube('body', [-7, 14, -7.25], [14, 6, 9.25], 'shell')
    m.bone('head', [0, 15, -7], role='head')
    m.cube('head', [-4, 9, -14], [8, 8, 7], 'blue')
    m.cube('head', [-3, 8, -16], [6, 4, 3], 'bone', label='muzzle')
    for s, side in [(-1, 'l'), (1, 'r')]:
        n = m.bone('horn_' + side, [s * 3, 16, -10], 'head', 'horn')
        m.cube(n, [(-9 if s < 0 else 3), 16, -12], [6, 3, 4.75], 'crystal', [0, 0, s * 15], [s * 3, 16, -10])
        m.cube(n, [(-9 if s < 0 else 7), 12, -10], [2, 5, 5], 'amber', [25, 0, 0], [s * 8, 16, -9])
        for idx, z in enumerate([-5, 6]):
            n = m.bone(f'leg_{side}{idx}', [s * 4, 10, z], role='leg')
            m.cube(n, [s * 4 - 1.5, 1, z - 1.5], [3, 9, 3], 'fur')
            m.cube(n, [s * 4 - 2, 0, z - 2], [4, 3, 4], 'basalt')
    m.bone('tail', [0, 12, 9], role='tail')
    m.cube('tail', [-1.5, 9, 9], [3, 5, 3], 'fur', [15, 0, 0], [0, 12, 9])
    return m


# 暮翅蛾：四翅各有转轴，翅不是共面复写；短胸、细腹和双触须形成昆虫轮廓。
def dusk_moth():
    m = Model('dusk_moth')
    m.bone('body', [0, 8, 0], 'root')
    m.cube('body', [-1.5, 7, -3], [3, 3, 5], 'fur')
    m.bone('abdomen', [0, 8, 2], role='tail')
    m.cube('abdomen', [-1, 7, 2], [2, 2, 6], 'basalt', [-8, 0, 0], [0, 8, 2])
    m.bone('head', [0, 8, -3], role='head')
    m.cube('head', [-2, 6.75, -5], [4, 3.5, 3], 'blue')
    for s, side in [(-1, 'l'), (1, 'r')]:
        n = m.bone('antenna_' + side, [s, 10, -4], 'head', 'antenna')
        m.cube(n, [s - .5, 10, -5], [1, 4, 1], 'amber', [-30, 0, -s * 25], [s, 10, -4])
        n = m.bone('wing_front_' + side, [s, 9, -1], role='wing_front')
        m.cube(n, [(-12 if s < 0 else 1), 8.8, -6], [11, .4, 7], 'wing', [0, s * 12, s * 8], [s, 9, -1])
        n = m.bone('wing_back_' + side, [s, 8, 1], role='wing_back')
        m.cube(n, [(-9 if s < 0 else 1), 7.8, 1], [8, .4, 6], 'violet', [0, -s * 18, -s * 5], [s, 8, 1])
    return m


# 玻行猎兽：狭长伏地躯干、犬形前颅和两节鞭尾，后腿向后收折。
def glass_stalker():
    m = Model('glass_stalker')
    m.bone('body', [0, 9, 0], 'root')
    m.cube('body', [-4, 6, -7], [8, 6, 15], 'blue')
    m.cube('body', [-3, 11, -5], [6, 2, 11], 'crystal')
    m.bone('head', [0, 10, -7], role='head')
    m.cube('head', [-3.5, 5.75, -13], [7, 6.5, 7], 'basalt')
    m.bone('jaw', [0, 7, -8], 'head', 'jaw')
    m.cube('jaw', [-3, 5, -14], [6, 2, 6], 'bone')
    for s, side in [(-1, 'l'), (1, 'r')]:
        for i, z in enumerate([-5, 5]):
            n = m.bone(f'leg_{side}{i}', [s * 3, 9, z], role='leg')
            m.cube(n, [s * 3 - 1.5, 2, z - 1.5], [3, 7, 3], 'blue', [(-15 if i == 0 else 23), 0, -s * 8], [s * 3, 9, z])
            m.cube(n, [s * 4 - 1.5, 0, z - 3], [3, 2, 5], 'basalt')
    m.bone('tail_0', [0, 8, 7], role='tail')
    m.cube('tail_0', [-1.5, 7, 7], [3, 3, 8], 'blue', [-10, 0, 0], [0, 8, 7])
    m.bone('tail_1', [0, 9, 14], 'tail_0', 'tail')
    m.cube('tail_1', [-1, 8, 14], [2, 2, 7], 'crystal', [12, 0, 0], [0, 9, 14])
    return m


# 针冠射手：三足茎兽，前向喷口和五根可展开冠针。
def needle_spitter():
    m = Model('needle_spitter')
    m.bone('body', [0, 10, 0], 'root')
    m.cube('body', [-3, 7, -3], [6, 9, 6], 'blue')
    m.bone('head', [0, 15, 0], role='head')
    m.cube('head', [-4, 13, -4], [8, 6, 8], 'shell')
    m.bone('muzzle', [0, 15, -4], 'head', 'jaw')
    m.cube('muzzle', [-2, 14, -7], [4, 3, 4], 'basalt')
    for i, (x, z, rz, rx) in enumerate([(-3, -1, -23, 0), (3, -1, 23, 0), (0, 3, 0, 24)]):
        n = m.bone(f'leg_{i}', [x, 10, z], role='leg')
        m.cube(n, [x - 1, 1, z - 1], [2, 10, 2], 'chitin', [rx, 0, rz], [x, 10, z])
        m.cube(n, [x - 2 + rz / 10, 0, z - 2 + rx / 10], [4, 2, 4], 'basalt')
    m.bone('crown', [0, 18, 0], 'head', 'crown')
    for i, (x, z) in enumerate([(-4, 0), (-2, 3), (0, 1), (2, 3), (4, 0)]):
        n = m.bone(f'needle_{i}', [x, 18, z], 'crown', 'needle')
        m.cube(n, [x - .5, 18, z - .5], [1, 6 + (i == 2) * 2, 1], 'amber', [12, 0, -x * 6], [x, 18, z])
    return m


# 砾背兽：扁宽的岩甲包覆矮肢，抬甲动作使用独立背甲轴。
def shardback():
    m = Model('shardback')
    m.bone('body', [0, 6, 0], 'root')
    m.cube('body', [-7, 3, -7], [14, 5, 15], 'basalt')
    m.bone('carapace', [0, 7, 5], role='shell')
    m.cube('carapace', [-9, 6, -8], [18, 4, 17], 'shell')
    m.cube('carapace', [-6, 10, -5], [12, 3, 11], 'chitin')
    m.cube('carapace', [-3, 13, -1], [6, 2, 5], 'amber')
    m.bone('head', [0, 5, -7], role='head')
    m.cube('head', [-4, 2, -11], [8, 5, 5], 'blue')
    for s, side in [(-1, 'l'), (1, 'r')]:
        for i, z in enumerate([-5, 5]):
            n = m.bone(f'leg_{side}{i}', [s * 6, 5, z], role='leg')
            m.cube(n, [s * 7 - 2, 0, z - 2.25], [4, 5, 4.5], 'basalt')
    return m


# 耀斑灵：开放的四分之三环和悬核，底部始终留出可见缺口。
def glare_wisp():
    m = Model('glare_wisp')
    m.bone('body', [0, 10, 0], 'root')
    m.cube('body', [-2, 8, -2], [4, 4, 4], 'amber', [0, 0, 45], [0, 10, 0], 'core')
    m.bone('ring', [0, 10, 0], role='crown')
    for i, (o, size, rot, pivot) in enumerate([
        ((-5, 16, -1.5), (10, 2, 3), (0, 0, 0), (0, 17, 0)),
        ((-8, 9, -1.5), (2, 7, 3), (0, 0, -12), (-7, 15, 0)),
        ((6, 9, -1.5), (2, 7, 3), (0, 0, 12), (7, 15, 0)),
        ((-6, 6, -1.5), (2, 4, 3), (0, 0, -35), (-5, 9, 0)),
        ((4, 6, -1.5), (2, 4, 3), (0, 0, 35), (5, 9, 0)),
    ]):
        m.cube('ring', o, size, 'crystal', rot, pivot)
    for s, side in [(-1, 'l'), (1, 'r')]:
        n = m.bone('spark_' + side, [s * 4, 5, 0], role='spark')
        m.cube(n, [s * 4 - .5, 3, -.5], [1, 2, 1], 'violet', [0, 0, s * 20], [s * 4, 5, 0])
    return m


# 裂层卫：六肢重甲前臂宽阔，单侧断脊体现不对称轮廓。
def fault_warden():
    m = Model('fault_warden')
    m.bone('body', [0, 12, 0], 'root')
    m.cube('body', [-10, 6, -10], [20, 11, 23], 'basalt')
    m.bone('carapace', [0, 15, 5], role='shell')
    m.cube('carapace', [-12, 15, -11], [24, 5, 25], 'shell')
    m.cube('carapace', [-8, 20, -7], [16, 4, 16], 'blue')
    for i, z in enumerate([-5, 1, 7]):
        for s in [-1, 1]:
            h = (8 - i) if s < 0 else (3 + i % 2)
            m.cube('carapace', [s * 6 - 1.5, 23, z], [3, h, 3], 'crystal', [8, 0, -s * 14], [s * 6, 23, z])
    m.bone('head', [0, 12, -10], role='head')
    m.cube('head', [-6, 5.75, -18], [12, 9.25, 9], 'chitin')
    m.cube('head', [-7, 12, -17], [14, 4, 7], 'shell')
    m.bone('jaw', [0, 8, -10], 'head', 'jaw')
    m.cube('jaw', [-5, 4, -18.25], [10, 3, 8.25], 'amber')
    for s, side in [(-1, 'l'), (1, 'r')]:
        for i, z in enumerate([-7, 1, 9]):
            n = m.bone(f'leg_{side}{i}', [s * 8, 11, z], role=('fore' if i == 0 else 'leg'))
            w = 6 if i == 0 else 4
            m.cube(n, [s * 10 - w / 2, 2, z - 2], [w, 10, 5], 'blue', [0, 0, -s * 12], [s * 8, 11, z])
            m.cube(n, [s * 12 - w / 2, 0, z - 4.25], [w, 4, 7.5], 'basalt')
    return m


# 狩镜者：反关节长腿、双节镰臂和倾斜开放镜架。
def mirror_huntress():
    m = Model('mirror_huntress')
    m.bone('body', [0, 22, 0], 'root')
    m.cube('body', [-4, 15, -3], [8, 12, 6], 'chitin')
    m.cube('body', [-6, 24, -3.25], [12, 4, 6.5], 'shell')
    m.bone('head', [0, 27, 0], role='head')
    m.cube('head', [-3, 27, -4], [6, 6, 5], 'bone')
    m.cube('head', [-4, 32, -2], [8, 2, 3.25], 'violet')
    m.bone('mirror', [0, 24, 4], role='crown')
    for o, size in [((-8, 22, 4), (2, 16, 2)), ((6, 22, 4), (2, 16, 2)), ((-6, 36, 4), (12, 2, 2)), ((-6, 22, 4), (12, 2, 2))]:
        m.cube('mirror', o, size, 'crystal', [12, 0, -18], [0, 24, 4])
    for s, side in [(-1, 'l'), (1, 'r')]:
        n = m.bone('leg_' + side, [s * 3, 17, 0], role='leg')
        m.cube(n, [s * 3 - 1, 8, -1], [2, 10, 3], 'blue', [-25, 0, -s * 8], [s * 3, 17, 0])
        k = m.bone('shin_' + side, [s * 4, 9, 3], n, 'shin')
        m.cube(k, [s * 4 - 1, 0, 1], [2, 10, 2], 'basalt', [15, 0, 0], [s * 4, 9, 3])
        m.cube(k, [s * 4 - 1.5, 0, -2], [3, 2, 5], 'crystal')
        n = m.bone('arm_' + side, [s * 5, 25, 0], role='arm')
        m.cube(n, [s * 6 - 1.5, 16, -1.5], [3, 10, 3], 'blue', [0, 0, s * 20], [s * 5, 25, 0])
        n2 = m.bone('scythe_' + side, [s * 9, 17, 0], n, 'scythe')
        m.cube(n2, [s * 9 - 1, 12, -8], [2, 6, 10], 'crystal', [-18, 0, 0], [s * 9, 17, 0])
        m.cube(n2, [s * 9 - .5, 13, -13], [1, 3, 6], 'violet', [20, 0, 0], [s * 9, 14, -8])
    return m


# 万相冕主：宽前胸窄后躯、四根支撑腿，四段可开合冠架围绕核心。
def parallax_regent():
    m = Model('parallax_regent')
    m.bone('body', [0, 19, 0], 'root')
    m.cube('body', [-13, 10, -14], [26, 15, 18], 'blue')
    m.cube('body', [-9, 12, 4], [18, 11, 17], 'basalt')
    m.cube('body', [-14, 24, -13], [28, 5, 15], 'shell')
    m.cube('body', [-8, 23, 3], [16, 4, 15], 'crystal')
    m.bone('head', [0, 20, -14], role='head')
    m.cube('head', [-7, 13, -24], [14, 11, 11], 'shell')
    m.cube('head', [-8, 22, -23], [16, 4, 9], 'basalt')
    m.bone('jaw', [0, 15, -15], 'head', 'jaw')
    m.cube('jaw', [-6, 10, -25], [12, 4, 11], 'amber')
    for s, side in [(-1, 'l'), (1, 'r')]:
        for i, z in enumerate([-9, 14]):
            n = m.bone(f'leg_{side}{i}', [s * (10 if i == 0 else 7), 17, z], role=('fore' if i == 0 else 'leg'))
            x = s * (12 if i == 0 else 9)
            m.cube(n, [x - 3, 4, z - 3], [6, 14, 6], 'blue', [0, 0, -s * 8], [x - s * 2, 17, z])
            m.cube(n, [x - 4, 0, z - 5], [8, 5, 10], 'basalt')
        m.cube('head', [s * 8 - 1.5, 22, -21], [3, 8, 3], 'crystal', [15, 0, -s * 25], [s * 7, 23, -18])
    m.bone('crown', [0, 28, -1], role='crown')
    for s, side in [(-1, 'l'), (1, 'r')]:
        n = m.bone('crown_' + side, [s * 7, 28, -1], 'crown', 'crown_leaf')
        m.cube(n, [s * 12 - 2, 28, -3], [4, 18, 4], 'crystal', [0, 0, -s * 15], [s * 7, 28, -1])
        m.cube(n, [(-14 if s < 0 else 3), 44, -3], [11, 3, 4], 'bone', [0, 0, -s * 8], [s * 12, 44, -1])
        n = m.bone('crown_back_' + side, [s * 5, 28, 6], 'crown', 'crown_leaf')
        m.cube(n, [s * 7 - 1.5, 28, 5], [3, 15, 3], 'blue', [15, 0, -s * 12], [s * 5, 28, 6])
    m.bone('core', [0, 37, -1], 'crown', 'core')
    m.cube('core', [-4, 33, -5], [8, 8, 8], 'amber', [0, 0, 45], [0, 37, -1], 'core')
    m.bone('tail_0', [0, 17, 20], role='tail')
    m.cube('tail_0', [-3, 14, 20], [6, 6, 9], 'blue', [-8, 0, 0], [0, 17, 20])
    m.bone('tail_1', [0, 17, 28], 'tail_0', 'tail')
    m.cube('tail_1', [-2, 15, 28], [4, 4, 8], 'crystal', [10, 0, 0], [0, 17, 28])
    return m


# 每套攻击遵循 Java 冻结契约：2秒/1秒命中、2.4秒/1.2秒命中。
def animations(m):
    result = {}
    zero = [0, 0, 0]
    roles = m.roles
    gait = .8 if m.name not in ('fault_warden', 'parallax_regent', 'shardback') else 1.2
    def add(name, length, loop=False):
        a = {'loop': loop, 'animation_length': length, 'bones': {}}
        result[f'animation.{m.name}.{name}'] = a
        return a['bones']
    def track(bones, name, channel, points):
        bones.setdefault(name, {})[channel] = {str(t): v for t, v in points}
    idle = add('idle', 3, True)
    track(idle, 'body', 'position', [(0, zero), (1.5, [0, .25, 0]), (3, zero)])
    for i, n in enumerate(roles.get('head', []) + roles.get('antenna', []) + roles.get('tail', [])):
        track(idle, n, 'rotation', [(0, zero), (1, [2, (-1 if i % 2 else 1) * 5, 0]), (2, [-2, 0, 0]), (3, zero)])
    walk = add('walk', gait, True)
    track(walk, 'body', 'position', [(0, zero), (gait / 4, [0, .5, 0]), (gait / 2, zero), (gait * .75, [0, .5, 0]), (gait, zero)])
    legs = roles.get('leg', []) + roles.get('fore', []) + roles.get('hind', [])
    for i, n in enumerate(legs):
        phase = 1 if i % 2 == 0 else -1
        if n.startswith(('leg_l', 'leg_r')) and n[-1].isdigit():
            phase = (1 if int(n[-1]) % 2 == 0 else -1) * (-1 if n.startswith('leg_r') else 1)
        amp = 22 if m.name != 'prism_hare' else 32
        track(walk, n, 'rotation', [(0, [amp * phase, 0, 0]), (gait / 2, [-amp * phase, 0, 0]), (gait, [amp * phase, 0, 0])])
    for i, n in enumerate(roles.get('tail', [])):
        track(walk, n, 'rotation', [(0, [0, 10, 0]), (gait / 2, [0, -10, 0]), (gait, [0, 10, 0])])
    if m.name == 'prism_hare':
        track(walk, 'body', 'position', [(0, zero), (.2, [0, 2.5, 0]), (.4, zero), (.6, [0, 2.5, 0]), (.8, zero)])
        for n in roles['hind']:
            track(walk, n, 'rotation', [(0, [-20, 0, 0]), (.2, [35, 0, 0]), (.4, [-20, 0, 0]), (.6, [35, 0, 0]), (.8, [-20, 0, 0])])
    if m.name in ('dusk_moth', 'glare_wisp'):
        track(idle, 'root', 'position', [(0, zero), (1.5, [0, 1.5, 0]), (3, zero)])
        track(walk, 'body', 'rotation', [(0, [8, 0, 0]), (gait / 2, [12, 0, 0]), (gait, [8, 0, 0])])
    if m.name == 'dusk_moth':
        for anim, duration in [(idle, 3), (walk, gait)]:
            for role in ('wing_front', 'wing_back'):
                for n in roles[role]:
                    sign = 1 if n.endswith('_l') else -1
                    times = [round(i * duration / 12, 5) for i in range(13)]
                    track(anim, n, 'rotation', [(t, [0, 0, sign * (30 if i % 2 == 0 else -25)]) for i, t in enumerate(times)])
    if m.name == 'glare_wisp':
        track(idle, 'ring', 'rotation', [(0, [0, -12, -5]), (1.5, [0, 12, 5]), (3, [0, -12, -5])])
        track(walk, 'ring', 'rotation', [(0, [0, -20, 10]), (gait / 2, [0, 20, -10]), (gait, [0, -20, 10])])
    attack = add('attack', 2)
    special = add('special', 2.4)
    track(attack, 'body', 'rotation', [(0, zero), (.8, [-8, 0, 0]), (.95, [-8, 0, 0]), (1, [12, 0, 0]), (1.2, [12, 0, 0]), (2, zero)])
    track(special, 'body', 'position', [(0, zero), (.9, [0, -1.5, 1]), (1.15, [0, -1.5, 1]), (1.2, [0, 1, -1.5]), (1.5, [0, .5, -1]), (2.4, zero)])
    for n in roles.get('head', []):
        track(attack, n, 'rotation', [(0, zero), (.8, [-18, 0, 0]), (.95, [-18, 0, 0]), (1, [22, 0, 0]), (1.25, [15, 0, 0]), (2, zero)])
        track(special, n, 'rotation', [(0, zero), (1, [-20, 0, 0]), (1.15, [-20, 0, 0]), (1.2, [10, 0, 0]), (1.6, [10, 0, 0]), (2.4, zero)])
    for n in roles.get('jaw', []):
        for anim, t in [(attack, 1), (special, 1.2)]:
            track(anim, n, 'rotation', [(0, zero), (t * .8, [25, 0, 0]), (t, [-8, 0, 0]), (t + .3, [5, 0, 0]), (t * 2, zero)])
    for i, n in enumerate(roles.get('fore', [])):
        sign = -1 if i % 2 else 1
        track(attack, n, 'rotation', [(0, zero), (.85, [-50, 0, sign * 12]), (.95, [-50, 0, sign * 12]), (1, [18, 0, -sign * 8]), (1.3, [10, 0, 0]), (2, zero)])
        track(special, n, 'rotation', [(0, zero), (1, [-25, 0, 0]), (1.2, [30, 0, 0]), (1.6, [0, 0, 0]), (2.4, zero)])
    for n in roles.get('shell', []):
        track(special, n, 'rotation', [(0, zero), (1, [-22, 0, 0]), (1.15, [-22, 0, 0]), (1.2, [5, 0, 0]), (1.5, [3, 0, 0]), (2.4, zero)])
    for i, n in enumerate(roles.get('needle', [])):
        track(attack, n, 'rotation', [(0, zero), (.8, [-40, 0, 0]), (.95, [-40, 0, 0]), (1, [12, 0, 0]), (1.3, [8, 0, 0]), (2, zero)])
        track(special, n, 'rotation', [(0, zero), (1, [-35, 0, (i - 2) * 12]), (1.2, [15, 0, (i - 2) * 8]), (1.6, [15, 0, 0]), (2.4, zero)])
    for i, n in enumerate(roles.get('arm', [])):
        s = 1 if n.endswith('_l') else -1
        track(attack, n, 'rotation', [(0, zero), (.8, [-55, 0, s * 25]), (.95, [-55, 0, s * 25]), (1, [30, -s * 25, -s * 10]), (1.3, [20, 0, 0]), (2, zero)])
        track(special, n, 'rotation', [(0, zero), (1, [-75, 0, s * 30]), (1.15, [-75, 0, s * 30]), (1.2, [-10, -s * 40, -s * 20]), (1.6, [-10, -s * 20, 0]), (2.4, zero)])
    for i, n in enumerate(roles.get('crown_leaf', [])):
        s = 1 if n.endswith('_l') else -1
        track(special, n, 'rotation', [(0, zero), (1, [0, 0, s * 25]), (1.15, [0, 0, s * 25]), (1.2, [12, 0, s * 10]), (1.6, [12, 0, s * 10]), (2.4, zero)])
    for n in roles.get('crown', []):
        track(special, n, 'rotation', [(0, zero), (1, [-15, 0, 0]), (1.15, [-15, 0, 0]), (1.2, [20, 0, 0]), (1.6, [20, 0, 0]), (2.4, zero)])
    for n in roles.get('core', []):
        track(idle, n, 'rotation', [(0, zero), (1.5, [0, 180, 0]), (3, [0, 360, 0])])
        track(special, n, 'scale', [(0, [1, 1, 1]), (1, [1.15, 1.15, 1.15]), (1.2, [.8, .8, .8]), (1.6, [1, 1, 1]), (2.4, [1, 1, 1])])
    if m.name == 'glare_wisp':
        for anim, t in [(attack, 1), (special, 1.2)]:
            track(anim, 'ring', 'rotation', [(0, zero), (t * .85, [0, 0, -25]), (t, [0, 0, 35]), (t * 1.4, [0, 0, 35]), (t * 2, zero)])
            track(anim, 'ring', 'scale', [(0, [1, 1, 1]), (t * .85, [.8, .8, .8]), (t, [1.2, 1.2, 1.2]), (t * 2, [1, 1, 1])])
    if m.name == 'dusk_moth':
        for role in ('wing_front', 'wing_back'):
            for n in roles[role]:
                s = 1 if n.endswith('_l') else -1
                track(special, n, 'rotation', [(0, zero), (.8, [0, 0, s * 65]), (1.2, [0, 0, -s * 25]), (1.8, [0, 0, s * 10]), (2.4, zero)])
                track(attack, n, 'rotation', [(0, zero), (.85, [0, 0, s * 55]), (1, [0, 0, -s * 40]), (1.5, [0, 0, s * 10]), (2, zero)])
    hurt = add('hurt', .5)
    track(hurt, 'body', 'rotation', [(0, zero), (.1, [-8, 0, 6]), (.25, [-3, 0, -3]), (.5, zero)])
    death = add('death', 1, 'hold_on_last_frame')
    track(death, 'root', 'rotation', [(0, zero), (.35, [0, 0, 8]), (.7, [0, 0, 75]), (1, [0, 0, 82])])
    half_width = max(abs(c['origin'][0]) for b in m.bones for c in b.get('cubes', []))
    track(death, 'root', 'position', [(0, zero), (.35, [0, half_width * .12, 0]), (.7, [0, half_width * .92, 0]), (1, [0, half_width, 0])])
    return {'format_version': '1.8.0', 'animations': result}


# 按实际面尺寸分配像素密度与1像素空边，绝不共享不同特征面的 UV。
def export(m):
    faces = []
    for b in m.bones:
        for ci, c in enumerate(b.get('cubes', [])):
            x, y, z = c['size']
            dims = {'north': (x, y), 'south': (x, y), 'east': (z, y), 'west': (z, y), 'up': (x, z), 'down': (x, z)}
            c['uv'] = {}
            for f, (w, h) in dims.items():
                w, h = max(1, math.ceil(w)), max(1, math.ceil(h))
                faces.append((b['name'], ci, c, f, w, h))
    area = sum((f[4] + 2) * (f[5] + 2) for f in faces)
    size = 128
    while area > size * size * .65:
        size *= 2
    while True:
        atlas = Image.new('RGBA', (size, size))
        x = y = row = 1
        rects = []
        failed = False
        for bn, ci, c, f, w, h in sorted(faces, key=lambda a: (-a[5], -a[4], a[0], a[1], a[3])):
            if x + w + 1 > size:
                x, y, row = 1, y + row + 2, 0
            if y + h + 1 > size:
                failed = True
                break
            c['uv'][f] = {'uv': [x, y], 'uv_size': [w, h]}
            tile = paint_face(w, h, c['_material'], c['_label'], f)
            atlas.paste(tile, (x, y))
            rects.append({'bone': bn, 'cube': ci, 'face': f, 'uv': [x, y, w, h], 'material': c['_material']})
            x += w + 2
            row = max(row, h)
        if not failed:
            break
        size *= 2
    bones = []
    for b in m.bones:
        out = {k: v for k, v in b.items() if k != 'cubes'}
        if b.get('cubes'):
            out['cubes'] = [{k: v for k, v in c.items() if not k.startswith('_')} for c in b['cubes']]
        bones.append(out)
    desc = {'identifier': f'geometry.hp_end_expansion.prismatic.{m.name}', 'texture_width': size, 'texture_height': size,
            'visible_bounds_width': 8, 'visible_bounds_height': 8, 'visible_bounds_offset': [0, 2, 0]}
    geo = {'format_version': '1.12.0', 'minecraft:geometry': [{'description': desc, 'bones': bones}]}
    for path, data in [(ASSETS / f'geo/prismatic/{m.name}.geo.json', geo),
                       (ASSETS / f'animations/prismatic/{m.name}.animation.json', animations(m)),
                       (ART / f'uv/{m.name}.json', {'texture_size': size, 'gutter': 1, 'faces': rects})]:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    texture = ASSETS / f'textures/entity/prismatic/{m.name}.png'
    texture.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(texture)
    if m.name in ('shardling', 'facet_ram', 'dusk_moth'):
        mask = Image.new('RGBA', atlas.size)
        md = ImageDraw.Draw(mask)
        for rect in rects:
            bn, face = rect['bone'], rect['face']
            x, y, w, h = rect['uv']
            selected = ((m.name == 'shardling' and bn in ('shell_0', 'shell_1') and face == 'up')
                        or (m.name == 'facet_ram' and bn.startswith('horn_') and face in ('north', 'up'))
                        or (m.name == 'dusk_moth' and bn == 'abdomen' and face in ('up', 'east', 'west')))
            if selected:
                color = PALETTES['violet'][4] if m.name == 'dusk_moth' else PALETTES['amber'][4]
                md.line((x + w // 2, y, x + w // 2, y + h - 1), fill=color)
                if w > 4:
                    md.point((x + w // 2 - 1, y + h // 2), fill=color)
        mask.save(texture.with_stem(m.name + '_glowmask'))
    return {'id': m.name, 'bones': len(bones), 'cubes': sum(len(b.get('cubes', [])) for b in bones), 'faces': len(rects), 'texture': size, 'roles': m.roles}


# 仅运行本任务资源生成，不修改语言、Java 或 data 内容。
if __name__ == '__main__':
    manifest = [export(make()) for make in (shardling, prism_hare, facet_ram, dusk_moth, glass_stalker, needle_spitter, shardback, glare_wisp, fault_warden, mirror_huntress, parallax_regent)]
    (ART / 'entity-manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(manifest, ensure_ascii=False))
