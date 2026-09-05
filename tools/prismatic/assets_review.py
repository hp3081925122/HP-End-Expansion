"""直接读取正式资源的 UV、骨骼、动作审计和贴图软件渲染；不替代客户端验证。"""
import base64
import hashlib
import json
import math
import uuid
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw, ImageFont
from assets_entities import ASSETS, ART
from assets_blocks import BLOCKS, ITEMS, VANILLA
import zipfile

FACES = ['north', 'south', 'east', 'west', 'up', 'down']
FONT = ImageFont.load_default(size=14)
SMALL = ImageFont.load_default(size=11)


# 以当前 JSON 为唯一渲染输入；骨骼旋转、立方体旋转和动画变换依次叠加。
def translate(v):
    a = np.eye(4)
    a[:3, 3] = v
    return a


def rotation(v):
    x, y, z = np.radians(v)
    cx, cy, cz, sx, sy, sz = np.cos(x), np.cos(y), np.cos(z), np.sin(x), np.sin(y), np.sin(z)
    rx = np.array([[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]])
    ry = np.array([[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]])
    rz = np.array([[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])
    return rz @ ry @ rx


# 正式资源全部使用线性数值关键帧，不依赖未解析的运行端表达式。
def sample(track, t, default):
    if track is None:
        return default
    if isinstance(track, list):
        return track
    points = sorted((float(k), v) for k, v in track.items())
    if t <= points[0][0]:
        return points[0][1]
    for (a, va), (b, vb) in zip(points, points[1:]):
        if t <= b:
            return (np.array(va) + (np.array(vb) - np.array(va)) * (t - a) / (b - a)).tolist()
    return points[-1][1]


def surface(c):
    x, y, z = c['origin']
    w, h, d = c['size']
    return {
        'north': [[x + w, y + h, z], [x, y + h, z], [x, y, z], [x + w, y, z]],
        'south': [[x, y + h, z + d], [x + w, y + h, z + d], [x + w, y, z + d], [x, y, z + d]],
        'east': [[x + w, y + h, z + d], [x + w, y + h, z], [x + w, y, z], [x + w, y, z + d]],
        'west': [[x, y + h, z], [x, y + h, z + d], [x, y, z + d], [x, y, z]],
        'up': [[x, y + h, z], [x + w, y + h, z], [x + w, y + h, z + d], [x, y + h, z + d]],
        'down': [[x, y, z + d], [x + w, y, z + d], [x + w, y, z], [x, y, z]],
    }


# 动画姿势与静态轴点共同变换，确保预览包含真实活动部位。
def model_faces(geo, animation=None, t=0):
    bones = geo['minecraft:geometry'][0]['bones']
    anim = (animation or {}).get('bones', {})
    matrices = {}
    out = []
    for b in bones:
        p = np.array(b.get('pivot', [0, 0, 0]))
        a = anim.get(b['name'], {})
        r = np.array(b.get('rotation', [0, 0, 0])) + sample(a.get('rotation'), t, [0, 0, 0])
        pos = sample(a.get('position'), t, [0, 0, 0])
        scale = np.eye(4)
        scale[[0, 1, 2], [0, 1, 2]] = sample(a.get('scale'), t, [1, 1, 1])
        bm = matrices.get(b.get('parent'), np.eye(4)) @ translate(pos) @ translate(p) @ rotation(r) @ scale @ translate(-p)
        matrices[b['name']] = bm
        for ci, c in enumerate(b.get('cubes', [])):
            cp = np.array(c.get('pivot', [0, 0, 0]))
            cm = bm @ translate(cp) @ rotation(c.get('rotation', [0, 0, 0])) @ translate(-cp)
            for f, vertices in surface(c).items():
                points = np.c_[np.array(vertices), np.ones(4)] @ cm.T
                out.append((points[:, :3], c['uv'][f], b['name'], ci, f))
    return out


# 逐像素 UV 采样与深度缓冲避免把重叠表面简单画成线框而漏掉材质问题。
def render(geo, texture, yaw=35, pitch=-22, animation=None, t=0, size=300):
    faces = model_faces(geo, animation, t)
    cam = (rotation([pitch, 0, 0]) @ rotation([0, yaw, 0]))[:3, :3]
    allp = np.concatenate([v @ cam.T for v, *_ in faces])
    lo, hi = allp.min(axis=0), allp.max(axis=0)
    scale = (size - 42) / max(hi[0] - lo[0], hi[1] - lo[1])
    center = (lo + hi) / 2
    buf = np.full((size, size, 4), [25, 26, 36, 255], dtype=np.uint8)
    depth = np.full((size, size), -1e10)
    tex = np.asarray(texture.convert('RGBA'))
    for vertices, uv, *_ in faces:
        p = vertices @ cam.T
        normal = np.cross(p[1] - p[0], p[2] - p[0])
        nlen = np.linalg.norm(normal)
        shade = .65 + .35 * abs(float(normal @ np.array([.25, .6, -.7])) / max(nlen, 1e-10))
        p[:, 0] = (p[:, 0] - center[0]) * scale + size / 2
        p[:, 1] = -(p[:, 1] - center[1]) * scale + size / 2
        u, v = uv['uv']
        w, h = uv['uv_size']
        uvp = np.array([[u, v], [u + w - .001, v], [u + w - .001, v + h - .001], [u, v + h - .001]])
        for tri in [[0, 1, 2], [0, 2, 3]]:
            q, tuv = p[tri], uvp[tri]
            x0, y0 = np.maximum(np.floor(q[:, :2].min(axis=0)).astype(int), 0)
            x1, y1 = np.minimum(np.ceil(q[:, :2].max(axis=0)).astype(int), size - 1)
            if x1 < x0 or y1 < y0:
                continue
            xx, yy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
            den = (q[1, 1] - q[2, 1]) * (q[0, 0] - q[2, 0]) + (q[2, 0] - q[1, 0]) * (q[0, 1] - q[2, 1])
            if abs(den) < 1e-8:
                continue
            aa = ((q[1, 1] - q[2, 1]) * (xx - q[2, 0]) + (q[2, 0] - q[1, 0]) * (yy - q[2, 1])) / den
            bb = ((q[2, 1] - q[0, 1]) * (xx - q[2, 0]) + (q[0, 0] - q[2, 0]) * (yy - q[2, 1])) / den
            cc = 1 - aa - bb
            z = -(aa * q[0, 2] + bb * q[1, 2] + cc * q[2, 2])
            uu = np.clip((aa * tuv[0, 0] + bb * tuv[1, 0] + cc * tuv[2, 0]).astype(int), 0, tex.shape[1] - 1)
            vv = np.clip((aa * tuv[0, 1] + bb * tuv[1, 1] + cc * tuv[2, 1]).astype(int), 0, tex.shape[0] - 1)
            pixels = tex[vv, uu].copy()
            pixels[:, :, :3] = (pixels[:, :, :3] * min(1, shade)).astype(np.uint8)
            mask = (aa >= 0) & (bb >= 0) & (cc >= 0) & (z > depth[y0:y1 + 1, x0:x1 + 1]) & (pixels[:, :, 3] > 127)
            buf[y0:y1 + 1, x0:x1 + 1][mask] = pixels[mask]
            depth[y0:y1 + 1, x0:x1 + 1][mask] = z[mask]
    return Image.fromarray(buf)


# 共面且同向的重叠面会闪烁；单独检测实际变换后的表面，关节体积嵌入不算错误。
def coplanar_overlaps(geo):
    surfaces = model_faces(geo)
    result = []
    for i, (v, _, bn, ci, fn) in enumerate(surfaces):
        n = np.cross(v[1] - v[0], v[2] - v[0])
        n /= np.linalg.norm(n)
        ax = (v[1] - v[0]) / np.linalg.norm(v[1] - v[0])
        ay = np.cross(n, ax)
        poly = np.c_[v @ ax, v @ ay]
        for w, _, b2, c2, f2 in surfaces[i + 1:]:
            if bn == b2 and ci == c2:
                continue
            nn = np.cross(w[1] - w[0], w[2] - w[0])
            nn /= np.linalg.norm(nn)
            if n @ nn < .99999 or np.max(np.abs((w - v[0]) @ n)) > .00001:
                continue
            clipped = [p for p in np.c_[w @ ax, w @ ay]]
            for a, b in zip(poly, np.roll(poly, -1, axis=0)):
                output = []
                for p, q in zip(clipped, clipped[1:] + clipped[:1]):
                    cross_p = (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0])
                    cross_q = (b[0] - a[0]) * (q[1] - a[1]) - (b[1] - a[1]) * (q[0] - a[0])
                    pin, qin = cross_p >= -1e-7, cross_q >= -1e-7
                    if pin:
                        output.append(p)
                    if pin != qin:
                        output.append(p + (q - p) * cross_p / (cross_p - cross_q))
                clipped = output
                if not clipped:
                    break
            if len(clipped) >= 3:
                p = np.array(clipped)
                area = abs(np.sum(p[:, 0] * np.roll(p[:, 1], -1) - p[:, 1] * np.roll(p[:, 0], -1))) / 2
                if area > .02:
                    result.append([f'{bn}/{ci}/{fn}', f'{b2}/{c2}/{f2}', round(area, 3)])
    return result


# 可编辑工程保存相同正式 PNG 和 per-face UV，UUID 由稳定资源路径导出。
def bbmodel(name, geo, anims, texture):
    bones = geo['minecraft:geometry'][0]['bones']
    desc = geo['minecraft:geometry'][0]['description']
    uid = lambda value: str(uuid.uuid5(uuid.NAMESPACE_URL, 'hp_end_expansion/prismatic/' + name + '/' + value))
    groups, elements = {}, []
    for b in bones:
        children = []
        group = {'name': b['name'], 'origin': b.get('pivot', [0, 0, 0]), 'rotation': b.get('rotation', [0, 0, 0]), 'uuid': uid(b['name']), 'export': True, 'isOpen': True, 'visibility': True, 'children': children}
        groups[b['name']] = group
        for i, c in enumerate(b.get('cubes', [])):
            key = uid(f'{b["name"]}/cube/{i}')
            el = {'name': f'{b["name"]}_{i}', 'type': 'cube', 'uuid': key, 'from': c['origin'], 'to': [a + b for a, b in zip(c['origin'], c['size'])],
                  'origin': c.get('pivot', b.get('pivot', [0, 0, 0])), 'rotation': c.get('rotation', [0, 0, 0]), 'box_uv': False,
                  'autouv': 0, 'rescale': False, 'locked': False, 'render_order': 'default', 'color': 0, 'faces': {}}
            for f, u in c['uv'].items():
                x, y = u['uv']
                w, h = u['uv_size']
                el['faces'][f] = {'uv': [x, y, x + w, y + h], 'texture': 0, 'rotation': 0}
            elements.append(el)
            children.append(key)
    outliner = []
    for b in bones:
        if b.get('parent'):
            groups[b['parent']]['children'].append(groups[b['name']])
        else:
            outliner.append(groups[b['name']])
    animations = []
    for n, a in anims['animations'].items():
        item = {'uuid': uid(n), 'name': n, 'loop': 'loop' if a.get('loop') is True else 'hold' if a.get('loop') == 'hold_on_last_frame' else 'once', 'length': a['animation_length'], 'snapping': 20, 'animators': {}}
        for bn, channels in a['bones'].items():
            animator = {'name': bn, 'type': 'bone', 'keyframes': []}
            for ch, values in channels.items():
                for time, value in values.items():
                    animator['keyframes'].append({'channel': ch, 'time': float(time), 'data_points': [dict(zip(['x', 'y', 'z'], [str(v) for v in value]))], 'uuid': uid(n + bn + ch + time), 'interpolation': 'linear'})
            item['animators'][uid(bn)] = animator
        animations.append(item)
    source = 'data:image/png;base64,' + base64.b64encode(texture.read_bytes()).decode()
    return {'meta': {'format_version': '4.10', 'model_format': 'geckolib_model', 'box_uv': False}, 'name': name,
            'model_identifier': desc['identifier'], 'visible_box': [8, 8, 2], 'resolution': {'width': desc['texture_width'], 'height': desc['texture_height']},
            'elements': elements, 'outliner': outliner, 'animations': animations,
            'textures': [{'name': name + '.png', 'id': '0', 'uuid': uid('texture'), 'width': desc['texture_width'], 'height': desc['texture_height'],
                          'uv_width': desc['texture_width'], 'uv_height': desc['texture_height'], 'source': source, 'relative_path': f'../../../src/main/resources/assets/hp_end_expansion/textures/entity/prismatic/{name}.png', 'render_mode': 'default'}]}


# 检查每个运行资源引用，覆盖全部面、全部关键帧、全套材料和原版状态分支。
def validate():
    entries = json.loads((ART / 'entity-manifest.json').read_text(encoding='utf-8'))
    summary, errors = [], []
    shape_hashes = set()
    (ART / 'previews').mkdir(parents=True, exist_ok=True)
    (ART / 'models').mkdir(parents=True, exist_ok=True)
    contact = Image.new('RGB', (1200, 4 * 340), '#171921')
    cd = ImageDraw.Draw(contact)
    for index, entry in enumerate(entries):
        name = entry['id']
        geo = json.loads((ASSETS / f'geo/prismatic/{name}.geo.json').read_text(encoding='utf-8'))
        animations = json.loads((ASSETS / f'animations/prismatic/{name}.animation.json').read_text(encoding='utf-8'))
        texpath = ASSETS / f'textures/entity/prismatic/{name}.png'
        texture = Image.open(texpath)
        bones = geo['minecraft:geometry'][0]['bones']
        names = {b['name'] for b in bones}
        if len(names) != len(bones):
            errors.append(name + ': duplicate bone')
        occupied = np.zeros(texture.size, dtype=np.uint8)
        rects, empty, mono = 0, 0, 0
        seen_cubes = set()
        for b in bones:
            if b.get('parent') and b['parent'] not in names:
                errors.append(name + ': missing parent ' + b['name'])
            for c in b.get('cubes', []):
                key = json.dumps({k: v for k, v in c.items() if k != 'uv'}, sort_keys=True)
                if key in seen_cubes:
                    errors.append(name + ': duplicate geometry cube')
                seen_cubes.add(key)
                if set(c['uv']) != set(FACES):
                    errors.append(name + ': missing face')
                for face, uv in c['uv'].items():
                    x, y = uv['uv']
                    w, h = uv['uv_size']
                    if min(x, y) < 1 or x + w >= texture.width or y + h >= texture.height:
                        errors.append(name + ': UV outside padded atlas')
                        continue
                    if occupied[y:y + h, x:x + w].any():
                        errors.append(name + ': UV overlap')
                    occupied[y:y + h, x:x + w] = 1
                    tile = texture.crop((x, y, x + w, y + h))
                    colors = tile.getcolors(w * h)
                    opaque = [(count, rgba) for count, rgba in colors if rgba[3] > 127]
                    if not opaque:
                        empty += 1
                        if not b['name'].startswith('wing_') or face in ('up', 'down'):
                            errors.append(name + ': empty face ' + b['name'] + '/' + face)
                    if w * h >= 12 and len(opaque) < 3 and opaque:
                        mono += 1
                        errors.append(name + ': insufficient face material ' + b['name'] + '/' + face)
                    rects += 1
        shape = hashlib.sha256(json.dumps([[(c['origin'], c['size']) for c in b.get('cubes', [])] for b in bones]).encode()).hexdigest()
        if shape in shape_hashes:
            errors.append(name + ': duplicated silhouette')
        shape_hashes.add(shape)
        overlaps = coplanar_overlaps(geo)
        if overlaps:
            errors.append(name + ': coplanar overlapping faces ' + json.dumps(overlaps))
        maskpath = texpath.with_stem(name + '_glowmask')
        if name in ('shardling', 'facet_ram', 'dusk_moth'):
            mask = Image.open(maskpath).convert('RGBA')
            if mask.size != texture.size or not mask.getbbox():
                errors.append(name + ': missing or invalid harvest glowmask')
            if np.count_nonzero(np.array(mask)[:, :, 3]) > texture.width * texture.height * .05:
                errors.append(name + ': harvest glowmask exceeds local organ area')
        expected = {f'animation.{name}.{suffix}' for suffix in ['idle', 'walk', 'attack', 'special', 'hurt', 'death']}
        if set(animations['animations']) != expected:
            errors.append(name + ': wrong animation names')
        for key, anim in animations['animations'].items():
            for bn, channels in anim['bones'].items():
                if bn not in names:
                    errors.append(name + ': unknown animated bone ' + bn)
                for ch, track in channels.items():
                    times = [float(t) for t in track]
                    if min(times) < 0 or max(times) > anim['animation_length']:
                        errors.append(name + ': keyframe outside duration ' + key)
            # 每种动作全时段采样，保证矩阵有限且几何仍有可见体积。
            for t in np.linspace(0, anim['animation_length'], 13):
                points = np.concatenate([v for v, *_ in model_faces(geo, anim, t)])
                if not np.isfinite(points).all() or np.max(np.ptp(points, axis=0)) < 1:
                    errors.append(name + ': invalid animation geometry ' + key)
        special = animations['animations'][f'animation.{name}.special']
        panels = [render(geo, texture, 0), render(geo, texture, 45), render(geo, texture, 135), render(geo, texture, 45, animation=special, t=1.15)]
        strip = Image.new('RGB', (1200, 340), '#191a24')
        sd = ImageDraw.Draw(strip)
        for i, (panel, label) in enumerate(zip(panels, ['FRONT', 'THREE QUARTER', 'BACK', 'SPECIAL WINDUP 1.15s'])):
            strip.paste(panel.convert('RGB'), (i * 300, 20))
            sd.text((i * 300 + 10, 4), name + ' / ' + label, font=SMALL, fill='#d8ceb0')
        strip.save(ART / f'previews/{name}-turnaround.png')
        # 为每种动作保存蓄势、命中与收招的真实纹理帧，便于逐行观察骨骼衔接。
        action_sheet = Image.new('RGB', (7 * 160, 6 * 180), '#191a24')
        ad = ImageDraw.Draw(action_sheet)
        for row_idx, suffix in enumerate(['idle', 'walk', 'attack', 'special', 'hurt', 'death']):
            anim = animations['animations'][f'animation.{name}.{suffix}']
            length = anim['animation_length']
            times = [0, .65, .95, 1, 1.2, 1.6, 2] if suffix == 'attack' else [0, .8, 1.15, 1.2, 1.5, 1.9, 2.4] if suffix == 'special' else [length * i / 6 for i in range(7)]
            for col_idx, t in enumerate(times):
                panel = render(geo, texture, 35, animation=anim, t=t, size=160)
                action_sheet.paste(panel.convert('RGB'), (col_idx * 160, row_idx * 180 + 18))
                ad.text((col_idx * 160 + 3, row_idx * 180 + 3), f'{suffix} / {t:.2f}s', font=SMALL, fill='#d8ceb0')
        action_sheet.save(ART / f'previews/{name}-actions.png')
        col, row = index % 4, index // 4
        contact.paste(panels[1].convert('RGB'), (col * 300, row * 340 + 20))
        cd.text((col * 300 + 10, row * 340 + 5), name, font=FONT, fill='#edac50')
        cd.text((col * 300 + 10, row * 340 + 322), f'{entry["bones"]} bones / {entry["cubes"]} cubes / {texture.width}px', font=SMALL, fill='#a8b8bf')
        model = bbmodel(name, geo, animations, texpath)
        (ART / f'models/{name}.bbmodel').write_text(json.dumps(model, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        if base64.b64decode(model['textures'][0]['source'].split(',')[1]) != texpath.read_bytes():
            errors.append(name + ': embedded texture differs')
        summary.append({**entry, 'uv_faces_checked': rects, 'intentional_invisible_wing_edges': empty, 'insufficient_face_material': mono, 'coplanar_overlapping_faces': overlaps,
                        'animation_samples': 78, 'geo_sha256': hashlib.sha256((ASSETS / f'geo/prismatic/{name}.geo.json').read_bytes()).hexdigest()})
    contact.crop((0, 0, 1200, math.ceil(len(entries) / 4) * 340)).save(ART / 'previews/entity-contact-sheet.png')
    # 校验所有方块状态、模型父级与纹理引用真实存在。
    vanilla = zipfile.ZipFile(VANILLA)
    vanilla_paths = set(vanilla.namelist())
    for path in list((ASSETS / 'blockstates').glob('prismatic_*.json')) + list((ASSETS / 'models/block/prismatic').glob('*.json')) + list((ASSETS / 'models/item').glob('prismatic_*.json')):
        data = json.loads(path.read_text(encoding='utf-8'))
        refs = []
        def visit(value):
            if isinstance(value, dict):
                for key, child in value.items():
                    if key in ('model', 'parent') and isinstance(child, str):
                        refs.append(('models', child, '.json'))
                    elif key == 'textures':
                        for texture in child.values():
                            if not texture.startswith('#'):
                                refs.append(('textures', texture, '.png'))
                    else:
                        visit(child)
            elif isinstance(value, list):
                for child in value:
                    visit(child)
        visit(data)
        for folder, ref, ext in refs:
            namespace, rpath = ref.split(':', 1) if ':' in ref else ('minecraft', ref)
            if namespace == 'hp_end_expansion':
                exists = (ASSETS / folder / (rpath + ext)).exists()
            else:
                exists = f'assets/{namespace}/{folder}/{rpath}{ext}' in vanilla_paths
            if not exists:
                errors.append(str(path.name) + ': missing reference ' + ref)
    if len(list((ASSETS / 'blockstates').glob('prismatic_*.json'))) != 27:
        errors.append('Blockstates count is not 27')
    for name in BLOCKS[20:26]:
        state = json.loads((ASSETS / f'blockstates/prismatic_{name}.json').read_text(encoding='utf-8'))
        if set(state['variants']) != {f'age={i}' for i in range(4)}:
            errors.append(name + ': wrong age variants')
        hashes = {hashlib.sha256((ASSETS / f'textures/block/prismatic/{name}_{age}.png').read_bytes()).hexdigest() for age in range(4)}
        if len(hashes) != 4:
            errors.append(name + ': repeated growth texture')
    # 纹理总览直接放大真实16px PNG，不用概念图替代正式资产。
    paths = sorted((ASSETS / 'textures/block/prismatic').glob('*.png')) + sorted((ASSETS / 'textures/item/prismatic').glob('*.png'))
    sw, sh = 140, 126
    sheet = Image.new('RGB', (sw * 8, sh * math.ceil(len(paths) / 8)), '#22232d')
    d = ImageDraw.Draw(sheet)
    for i, path in enumerate(paths):
        x, y = (i % 8) * sw, (i // 8) * sh
        for yy in range(0, 96, 8):
            for xx in range(0, 96, 8):
                d.rectangle((x + xx + 22, y + yy, x + xx + 29, y + yy + 7), fill='#383843' if (xx + yy) % 16 == 0 else '#30303a')
        im = Image.open(path).convert('RGBA').resize((96, 96), Image.Resampling.NEAREST)
        sheet.paste(im, (x + 22, y), im)
        d.text((x + 5, y + 100), path.stem, font=SMALL, fill='#e3d7bd')
    sheet.save(ART / 'previews/block-item-contact-sheet.png')
    report = {'entities': summary, 'blocks': 27, 'materials': 16, 'spawn_eggs': 11, 'growth_textures': 24,
              'errors': errors, 'status': 'PASS' if not errors else 'FAIL', 'limits': ['Software renderer is an offline inspection aid, not GeckoLib or Blockbench runtime.', 'Blockbench MCP was not exposed to this subtask. Editable projects are saved for later import.']}
    (ART / 'validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({'status': report['status'], 'entities': len(summary), 'errors': errors}, ensure_ascii=False))
    if errors:
        raise SystemExit(1)


if __name__ == '__main__':
    validate()
