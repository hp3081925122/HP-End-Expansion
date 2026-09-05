"""折光荒原方块、植被与材料的确定性手工像素资源。"""
from pathlib import Path
import json
import zipfile
from PIL import Image, ImageDraw
from assets_entities import ASSETS, ART, PALETTES, paint_face

VANILLA = Path('C:/Users/30819/.gradle/caches/fabric-loom-backup-20260822/1.21.1/minecraft-client.jar')
NS = 'hp_end_expansion'
BLOCKS = ['prism_soil', 'chalkstone', 'chalkstone_bricks', 'chalkstone_stairs', 'chalkstone_slab', 'chalkstone_wall',
          'cracked_bricks', 'chiseled_chalkstone', 'lumen_ore', 'lumen_block', 'dusk_glass', 'prism_log',
          'stripped_prism_log', 'prism_planks', 'prism_stairs', 'prism_slab', 'prism_leaves', 'prism_lamp',
          'survey_marker', 'regent_altar', 'prism_grass', 'mica_reed', 'lantern_bloom', 'glass_fern',
          'shard_cactus', 'dusk_bloom', 'prism_sapling']
ITEMS = ['crystal_bud', 'crystal_shard', 'prism_meal', 'mica_sheet', 'lumen_dust', 'prism_needle', 'dusk_bud',
         'dusk_silk', 'horn_fragment', 'carapace', 'fault_sigil', 'mirror_sigil', 'lumen_crystal', 'prism_key',
         'regent_core', 'refraction_charm']


# 所有资源使用 UTF-8；仅写入 PB3 独占路径。
def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


# 16像素岩石、木材与矿物材质分别使用沉积线、砖缝、年轮和斜晶面。
def block_texture(name):
    kind = 'shell'
    if name.startswith('prism_soil'):
        kind = 'basalt'
    if name.startswith('prism_log') or name.startswith('stripped_prism_log'):
        kind = 'bone'
    if name.startswith('prism_planks') or name == 'prism_leaves':
        kind = 'blue'
    if name in ('lumen_block', 'prism_lamp', 'regent_altar_top'):
        kind = 'amber'
    im = paint_face(16, 16, kind, name, 'north')
    d = ImageDraw.Draw(im)
    c = PALETTES[kind]
    if name.startswith('prism_soil'):
        for x, y in [(2, 3), (10, 1), (6, 8), (13, 11), (2, 13)]:
            d.line((x, y, x + 2, y - 1, x + 3, y + 1), fill=PALETTES['shell'][2])
        if name.endswith('_top'):
            d.line((0, 8, 5, 6, 9, 8, 15, 5), fill=PALETTES['amber'][2])
            d.line((4, 0, 6, 4, 5, 6), fill=PALETTES['blue'][3])
    if name == 'chalkstone':
        for y in (4, 10):
            d.line((0, y, 5, y + 1, 11, y, 15, y + 1), fill=c[1])
            d.line((0, y - 1, 5, y, 11, y - 1, 15, y), fill=c[4])
    if name in ('chalkstone_bricks', 'cracked_bricks'):
        for y in (0, 8):
            d.line((0, y, 15, y), fill=c[0])
            d.line((0, y + 1, 15, y + 1), fill=c[4])
            x = 3 if y == 0 else 11
            d.line((x, y, x, y + 7), fill=c[0])
            d.line((x + 1, y + 1, x + 1, y + 7), fill=c[3])
        if name == 'cracked_bricks':
            d.line((6, 0, 7, 3, 5, 5, 7, 8, 6, 11, 9, 15), fill=c[0])
            d.line((7, 7, 11, 6, 13, 3), fill=c[0])
    if name == 'chiseled_chalkstone':
        d.rectangle((1, 1, 14, 14), outline=c[1])
        d.polygon([(8, 3), (12, 8), (8, 12), (3, 8)], outline=c[0])
        d.line((8, 4, 11, 8, 8, 11), fill=c[4])
        d.rectangle((7, 7, 8, 8), fill=PALETTES['amber'][2])
    if name == 'lumen_ore':
        for x, y, w, h in [(2, 3, 4, 4), (10, 1, 3, 5), (7, 9, 5, 5), (1, 12, 3, 2)]:
            d.polygon([(x, y + 1), (x + w - 1, y), (x + w, y + h - 1), (x + 1, y + h)], fill=PALETTES['amber'][2])
            d.line((x + 1, y + 1, x + 1, y + h - 1), fill=PALETTES['amber'][4])
    if name == 'lumen_block':
        d.polygon([(0, 0), (8, 2), (6, 8), (0, 13)], fill=PALETTES['amber'][1])
        d.line((8, 0, 6, 8, 15, 15), fill=PALETTES['amber'][4])
        d.line((6, 8, 0, 13), fill=PALETTES['amber'][3])
        d.line((8, 2, 15, 0), fill=PALETTES['amber'][0])
    if name in ('prism_log', 'stripped_prism_log'):
        for x in (1, 5, 10, 14):
            d.line((x, 0, x - 1, 5, x + 1, 9, x, 15), fill=c[0 if name == 'prism_log' else 1])
            if name == 'prism_log':
                d.line((x + 1, 0, x, 5, x + 2, 9, x + 1, 15), fill=PALETTES['blue'][2])
        d.rectangle((7, 4, 9, 8), outline=c[3])
        d.line((8, 5, 8, 7), fill=c[0])
    if name in ('prism_log_top', 'stripped_prism_log_top'):
        for inset in (0, 3, 6):
            d.rectangle((inset, inset, 15 - inset, 15 - inset), outline=c[1])
            if inset < 6:
                d.line((inset + 1, inset + 1, 14 - inset, inset + 1), fill=c[4])
        d.line((8, 8, 15, 13), fill=c[0])
    if name == 'prism_planks':
        for y in (0, 4, 8, 12):
            d.line((0, y, 15, y), fill=c[0])
            d.line((0, y + 1, 15, y + 1), fill=c[3])
            x = (y * 3 + 2) % 16
            d.line((x, y, x, y + 3), fill=c[0])
        d.line((3, 6, 6, 6), fill=c[4])
        d.line((10, 10, 13, 10), fill=c[1])
    if name == 'prism_leaves':
        im = Image.new('RGBA', (16, 16))
        d = ImageDraw.Draw(im)
        for x, y in [(1, 1), (8, 0), (4, 6), (11, 7), (0, 12), (8, 13)]:
            d.polygon([(x, y + 1), (x + 5, y), (x + 6, y + 2), (x + 2, y + 3)], fill=c[2])
            d.line((x + 1, y + 1, x + 4, y + 1), fill=c[4])
            d.line((x + 2, y + 2, x + 5, y + 2), fill=c[0])
    if name == 'dusk_glass':
        im = Image.new('RGBA', (16, 16), (69, 58, 85, 48))
        d = ImageDraw.Draw(im)
        d.rectangle((0, 0, 15, 15), outline=(73, 60, 89, 230))
        d.line((1, 1, 14, 1), fill=(159, 144, 169, 205))
        d.line((2, 10, 7, 5), fill=(163, 156, 173, 120))
        d.line((6, 12, 12, 6), fill=(163, 156, 173, 90))
    if name == 'prism_lamp':
        frame = PALETTES['basalt']
        d.rectangle((0, 0, 15, 15), outline=frame[0], width=2)
        d.rectangle((3, 3, 12, 12), outline=PALETTES['amber'][4])
        d.polygon([(8, 4), (11, 8), (8, 11), (4, 8)], fill=PALETTES['amber'][4])
        for x, y in [(1, 1), (13, 1), (1, 13), (13, 13)]:
            d.point((x, y), fill=PALETTES['blue'][3])
    if name.startswith('survey_marker'):
        d.rectangle((1, 1, 14, 14), outline=PALETTES['blue'][1])
        d.line((7, 3, 7, 12), fill=PALETTES['basalt'][1], width=2)
        d.polygon([(8, 2), (12, 7), (9, 7), (9, 11), (6, 11), (6, 7), (3, 7)], outline=PALETTES['amber'][2])
        if name.endswith('_top'):
            d.line((2, 8, 13, 8), fill=PALETTES['blue'][1])
    if name.startswith('regent_altar'):
        c = PALETTES['basalt']
        if name.endswith('_top'):
            d.rectangle((0, 0, 15, 15), outline=c[0], width=3)
            d.polygon([(8, 3), (12, 8), (8, 12), (3, 8)], outline=PALETTES['bone'][4])
            d.rectangle((6, 6, 9, 9), fill=c[0])
            d.point((7, 7), fill=PALETTES['violet'][4])
        else:
            d.rectangle((0, 0, 15, 15), fill=c[1])
            for y in (1, 13):
                d.line((0, y, 15, y), fill=PALETTES['shell'][2], width=2)
            d.polygon([(8, 4), (11, 8), (8, 11), (4, 8)], outline=PALETTES['amber'][2])
            d.line((2, 4, 2, 11), fill=c[3])
            d.line((13, 4, 13, 11), fill=c[0])
    return im


# 各植物先画独立株型，再增加节、叶或成熟器官，生长期不会只改变颜色。
def plant_texture(name, age=3):
    im = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(im)
    b, a, v, c = (PALETTES[n] for n in ('blue', 'amber', 'violet', 'crystal'))
    top = 12 - age * 3
    if name == 'prism_grass':
        for x, height, lean in [(7, 4 + age * 3, -2), (9, 3 + age * 2, 3), (5, 3 + age * 2, -3)]:
            d.polygon([(x - 1, 15), (x + lean, 15 - height), (x + 1, 15)], fill=a[2])
            d.line((x, 14, x + lean, 16 - height), fill=a[4])
        if age == 3:
            d.rectangle((4, 5, 6, 7), fill=c[3])
            d.point((4, 5), fill=c[4])
    elif name == 'mica_reed':
        for x, offset in [(5, 1), (10, -2)]:
            d.line((x, 15, x, max(1, top + offset)), fill=b[1], width=2)
            d.line((x, 15, x, max(1, top + offset)), fill=b[3])
            for y in range(14, max(1, top + offset), -4):
                d.line((x - 1, y, x + 2, y), fill=c[4])
        if age >= 2:
            d.polygon([(6, 8), (2, 5), (3, 9)], fill=c[2])
            d.polygon([(10, 5), (14, 3), (13, 7)], fill=c[3])
        if age == 3:
            d.line((8, 1, 12, 1), fill=a[4])
    elif name == 'lantern_bloom':
        d.line((7, 15, 8, top + 3), fill=b[2], width=2)
        d.polygon([(7, 12), (2, 10), (4, 14)], fill=b[3])
        d.polygon([(8, 10), (12, 8), (11, 12)], fill=b[1])
        w = 2 + age
        d.polygon([(8 - w, top), (8 + w, top), (8 + w - 1, top + 4), (8, top + 6), (8 - w + 1, top + 4)], fill=c[1])
        d.line((8 - w, top, 8 + w, top), fill=c[4])
        if age == 3:
            d.rectangle((6, top + 1, 10, top + 3), fill=a[3])
            d.line((7, top + 1, 9, top + 1), fill=a[4])
    elif name == 'glass_fern':
        d.line((7, 15, 8, top), fill=b[1])
        for i in range(2 + age):
            y = 13 - i * 2
            w = max(2, 6 - i)
            d.line((7, y, 7 - w, y - 2), fill=c[2], width=2)
            d.line((8, y - 1, 8 + w, y - 3), fill=c[3], width=2)
            d.point((7 - w, y - 2), fill=c[4])
            d.point((8 + w, y - 3), fill=c[4])
        if age == 3:
            d.point((8, 2), fill=a[4])
            d.point((2, 8), fill=a[3])
    elif name == 'shard_cactus':
        d.rectangle((5, top, 10, 15), fill=c[1])
        d.line((6, top, 6, 15), fill=c[4])
        d.line((9, top + 1, 9, 15), fill=c[0])
        for y in range(14, top, -4):
            d.line((5, y, 10, y), fill=b[0])
            d.line((3, y - 1, 5, y - 2), fill=a[3])
            d.line((10, y - 3, 12, y - 4), fill=a[4])
        if age == 3:
            d.polygon([(6, 2), (8, 0), (9, 2)], fill=a[4])
    elif name == 'dusk_bloom':
        d.line((6, 15, 6, top + 2, 9, top + 1, 11, top + 3), fill=b[2], width=2)
        d.polygon([(5, 13), (1, 10), (2, 14)], fill=b[2])
        d.polygon([(7, 11), (11, 9), (9, 13)], fill=b[3])
        d.polygon([(9, top + 3), (13, top + 3), (14, top + 6), (11, top + 9), (8, top + 6)], fill=v[1])
        d.line((10, top + 4, 10, top + 7), fill=v[3])
        if age == 3:
            d.point((11, top + 8), fill=v[4])
            d.point((12, top + 7), fill=a[3])
    elif name == 'prism_sapling':
        d.line((7, 15, 8, 5), fill=PALETTES['bone'][3], width=2)
        d.line((7, 10, 3, 7), fill=PALETTES['bone'][1])
        for x, y, w in [(2, 6, 6), (6, 3, 7), (8, 8, 6)]:
            d.polygon([(x, y), (x + w - 1, y - 1), (x + w, y + 1), (x + 1, y + 2)], fill=b[2])
            d.line((x + 1, y, x + w - 2, y), fill=b[4])
    return im


# 每个材料独立设计识别剪影；不依靠统一晶体图标换色区分物品。
def item_texture(name):
    im = Image.new('RGBA', (16, 16))
    d = ImageDraw.Draw(im)
    a, b, v, s = (PALETTES[n] for n in ('amber', 'blue', 'violet', 'shell'))
    if name in ('crystal_shard', 'lumen_crystal', 'prism_needle'):
        points = {'crystal_shard': [(3, 13), (4, 5), (9, 1), (12, 10), (8, 14)],
                  'lumen_crystal': [(3, 7), (7, 1), (11, 3), (13, 11), (8, 15), (3, 12)],
                  'prism_needle': [(2, 14), (6, 6), (13, 1), (11, 8), (4, 15)]}[name]
        c = PALETTES['crystal'] if name == 'crystal_shard' else a
        d.polygon(points, fill=c[1], outline=c[0])
        d.line(points[1:4], fill=c[4])
        d.line((7, 5, 8, 11), fill=c[3])
        d.point((6, 8), fill=c[2])
    elif name in ('crystal_bud', 'dusk_bud'):
        c = a if name == 'crystal_bud' else v
        d.polygon([(4, 5), (7, 2), (11, 4), (12, 9), (9, 13), (5, 11)], fill=c[2], outline=c[0])
        d.line((6, 5, 8, 3, 9, 6, 8, 10), fill=c[4])
        d.line((7, 12, 7, 14), fill=b[2])
        d.polygon([(6, 12), (2, 10), (3, 13)], fill=b[3])
    elif name in ('prism_meal', 'lumen_dust'):
        c = s if name == 'prism_meal' else a
        d.polygon([(1, 13), (3, 9), (6, 8), (8, 5), (12, 9), (14, 13), (11, 15), (4, 15)], fill=c[2], outline=c[0])
        for x, y in [(4, 11), (7, 8), (9, 12), (12, 11), (5, 14), (10, 7), (3, 5), (11, 2), (7, 3)]:
            d.point((x, y), fill=c[4])
            d.point((x + 1, y + 1), fill=c[1])
    elif name == 'mica_sheet':
        for off in (3, 1, 0):
            d.polygon([(2, 4 + off), (11, 1 + off), (14, 8 + off), (5, 12 + off)], fill=b[2], outline=b[0])
        d.line((3, 5, 10, 3, 12, 7), fill=b[4])
        d.line((7, 6, 9, 9), fill=s[3])
    elif name == 'dusk_silk':
        d.polygon([(4, 3), (10, 1), (13, 5), (12, 12), (7, 15), (2, 11)], fill=v[1], outline=v[0])
        for y in (4, 7, 10):
            d.line((4, y, 8, y - 1, 11, y + 1), fill=v[3])
            d.line((4, y + 1, 8, y, 11, y + 2), fill=v[2])
        d.line((3, 11, 4, 14, 1, 15), fill=v[4])
    elif name == 'horn_fragment':
        d.polygon([(2, 2), (7, 3), (9, 7), (14, 9), (13, 13), (8, 12), (5, 8)], fill=s[2], outline=s[0])
        d.line((4, 4, 7, 8, 12, 10), fill=s[4])
        d.line((8, 9, 7, 11), fill=b[1])
    elif name == 'carapace':
        d.polygon([(2, 5), (6, 1), (12, 2), (15, 8), (12, 13), (5, 15), (1, 10)], fill=b[2], outline=b[0])
        d.line((3, 5, 7, 3, 11, 4), fill=b[4])
        for y in (7, 10):
            d.line((3, y, 8, y + 1, 13, y - 1), fill=b[0])
            d.line((3, y - 1, 8, y, 13, y - 2), fill=b[3])
    elif name in ('fault_sigil', 'mirror_sigil'):
        c = s if name == 'fault_sigil' else b
        d.polygon([(4, 1), (11, 1), (15, 5), (13, 12), (8, 15), (2, 12), (1, 5)], fill=c[1], outline=c[0])
        d.polygon([(5, 3), (10, 3), (12, 6), (11, 11), (8, 13), (4, 10), (3, 6)], outline=c[4])
        if name == 'fault_sigil':
            d.line((7, 4, 6, 7, 9, 8, 8, 11), fill=a[4], width=2)
        else:
            d.polygon([(8, 4), (11, 8), (8, 11), (5, 8)], fill=v[2], outline=v[4])
    elif name == 'prism_key':
        d.polygon([(3, 1), (7, 1), (9, 4), (7, 7), (4, 7), (1, 4)], fill=a[2], outline=a[0])
        d.rectangle((4, 3, 6, 4), fill=v[3])
        d.line((7, 6, 13, 12), fill=a[4], width=2)
        d.line((10, 10, 8, 12), fill=b[3], width=2)
        d.line((13, 12, 11, 14), fill=b[4], width=2)
    elif name == 'regent_core':
        d.polygon([(8, 1), (13, 4), (15, 9), (10, 14), (4, 13), (1, 7), (4, 3)], fill=b[0], outline=s[2])
        d.polygon([(8, 3), (12, 7), (9, 12), (4, 9), (4, 5)], fill=a[2], outline=a[4])
        d.line((8, 4, 7, 8, 9, 11), fill=a[4])
        d.point((5, 7), fill=v[4])
    elif name == 'refraction_charm':
        d.line((3, 7, 2, 3, 5, 1, 10, 1, 13, 3, 12, 7), fill=b[3])
        d.polygon([(8, 5), (13, 9), (8, 15), (3, 9)], fill=s[1], outline=s[4])
        d.polygon([(8, 7), (11, 9), (8, 12), (5, 9)], fill=v[2], outline=v[4])
        d.point((8, 8), fill=a[4])
    return im


# 原版资源只用于当前1.21.1的楼梯、台阶、墙状态格式，不读取其他任务的设计。
def generate():
    z = zipfile.ZipFile(VANILLA)
    assert json.loads(z.read('version.json'))['id'] == '1.21.1'
    texdir = ASSETS / 'textures/block/prismatic'
    texdir.mkdir(parents=True, exist_ok=True)
    modeldir = ASSETS / 'models/block/prismatic'
    itemdir = ASSETS / 'models/item'
    statedir = ASSETS / 'blockstates'
    for name in ['prism_soil', 'prism_soil_top', 'chalkstone', 'chalkstone_bricks', 'cracked_bricks',
                 'chiseled_chalkstone', 'lumen_ore', 'lumen_block', 'dusk_glass', 'prism_log', 'prism_log_top',
                 'stripped_prism_log', 'stripped_prism_log_top', 'prism_planks', 'prism_leaves', 'prism_lamp',
                 'survey_marker', 'survey_marker_top', 'regent_altar', 'regent_altar_top']:
        block_texture(name).save(texdir / (name + '.png'))
    for name in BLOCKS[:20]:
        if name.endswith(('_stairs', '_slab', '_wall')):
            continue
        tex = f'{NS}:block/prismatic/{name}'
        data = {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex}}
        if name in ('prism_soil', 'survey_marker', 'regent_altar'):
            data = {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'side': tex, 'bottom': tex, 'top': tex + '_top'}}
        if name.endswith('_log'):
            data = {'parent': 'minecraft:block/cube_column', 'textures': {'side': tex, 'end': tex + '_top'}}
            write(statedir / f'prismatic_{name}.json', {'variants': {'axis=y': {'model': f'{NS}:block/prismatic/{name}'},
                  'axis=x': {'model': f'{NS}:block/prismatic/{name}', 'x': 90, 'y': 90},
                  'axis=z': {'model': f'{NS}:block/prismatic/{name}', 'x': 90}}})
        else:
            write(statedir / f'prismatic_{name}.json', {'variants': {'': {'model': f'{NS}:block/prismatic/{name}'}}})
        if name == 'dusk_glass':
            data['render_type'] = 'minecraft:translucent'
        if name == 'prism_leaves':
            data['render_type'] = 'minecraft:cutout_mipped'
        write(modeldir / f'{name}.json', data)
        write(itemdir / f'prismatic_{name}.json', {'parent': f'{NS}:block/prismatic/{name}'})
    for name, source, base, kind in [('chalkstone_stairs', 'stone_brick_stairs', 'chalkstone_bricks', 'stairs'),
                                   ('prism_stairs', 'oak_stairs', 'prism_planks', 'stairs'),
                                   ('chalkstone_slab', 'stone_brick_slab', 'chalkstone_bricks', 'slab'),
                                   ('prism_slab', 'oak_slab', 'prism_planks', 'slab'),
                                   ('chalkstone_wall', 'stone_brick_wall', 'chalkstone_bricks', 'wall')]:
        state = z.read(f'assets/minecraft/blockstates/{source}.json').decode()
        state = state.replace(f'minecraft:block/{source}', f'{NS}:block/prismatic/{name}')
        if kind == 'slab':
            vanilla_full = 'stone_bricks' if base == 'chalkstone_bricks' else 'oak_planks'
            state = state.replace('minecraft:block/' + vanilla_full, f'{NS}:block/prismatic/{base}')
        write(statedir / f'prismatic_{name}.json', json.loads(state))
        tex = f'{NS}:block/prismatic/{base}'
        if kind == 'stairs':
            for suffix, parent in [('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')]:
                write(modeldir / f'{name}{suffix}.json', {'parent': 'minecraft:block/' + parent, 'textures': {'bottom': tex, 'top': tex, 'side': tex}})
        if kind == 'slab':
            for suffix, parent in [('', 'slab'), ('_top', 'slab_top')]:
                write(modeldir / f'{name}{suffix}.json', {'parent': 'minecraft:block/' + parent, 'textures': {'bottom': tex, 'top': tex, 'side': tex}})
        if kind == 'wall':
            for suffix, parent in [('_post', 'template_wall_post'), ('_side', 'template_wall_side'), ('_side_tall', 'template_wall_side_tall'), ('_inventory', 'wall_inventory')]:
                write(modeldir / f'{name}{suffix}.json', {'parent': 'minecraft:block/' + parent, 'textures': {'wall': tex}})
        write(itemdir / f'prismatic_{name}.json', {'parent': f'{NS}:block/prismatic/{name}' + ('_inventory' if kind == 'wall' else '')})
    for name in BLOCKS[20:]:
        if name == 'prism_sapling':
            plant_texture(name).save(texdir / f'{name}.png')
            write(modeldir / f'{name}.json', {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': f'{NS}:block/prismatic/{name}'}})
            state = {'variants': {'': {'model': f'{NS}:block/prismatic/{name}'}}}
            item_tex = f'{NS}:block/prismatic/{name}'
        else:
            state = {'variants': {}}
            for age in range(4):
                modelname = f'{name}_{age}'
                plant_texture(name, age).save(texdir / f'{modelname}.png')
                write(modeldir / f'{modelname}.json', {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': f'{NS}:block/prismatic/{modelname}'}})
                state['variants'][f'age={age}'] = {'model': f'{NS}:block/prismatic/{modelname}'}
            item_tex = f'{NS}:block/prismatic/{name}_3'
        write(statedir / f'prismatic_{name}.json', state)
        write(itemdir / f'prismatic_{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': item_tex}})
    material_dir = ASSETS / 'textures/item/prismatic'
    material_dir.mkdir(parents=True, exist_ok=True)
    for name in ITEMS:
        item_texture(name).save(material_dir / f'{name}.png')
        write(itemdir / f'prismatic_{name}.json', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:item/prismatic/{name}'}})
    entities = json.loads((ART / 'entity-manifest.json').read_text(encoding='utf-8'))
    for entry in entities:
        write(itemdir / f'prismatic_{entry["id"]}_spawn_egg.json', {'parent': 'minecraft:item/template_spawn_egg'})
    write(ART / 'block-item-manifest.json', {'blocks': BLOCKS, 'materials': ITEMS, 'spawn_eggs': [e['id'] for e in entities],
          'vanilla_state_source': 'Minecraft 1.21.1 client.jar blockstates, version.json verified'})
    print(f'Generated {len(BLOCKS)} blockstates, {len(ITEMS)} materials and {len(entities)} spawn egg models')


if __name__ == '__main__':
    generate()
