from pathlib import Path
import json
import re
import sys
import zipfile
import hashlib

# 本审查交叉核对注册清单、资源引用、语言和战利品，不代替服务端或视觉验证。
ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'src/main/resources'
sys.path.insert(0, str(Path(__file__).parent))
source = (Path(__file__).parent / 'generate_data.py').read_text(encoding='utf-8')
namespace = {'__file__': str(Path(__file__).parent / 'generate_data.py')}
exec(source[:source.index('# 所有注册路径')], namespace)
BLOCKS, ITEMS, ENTITIES = (namespace[key] for key in ['BLOCKS', 'ITEMS', 'ENTITIES'])
MOD = 'hp_end_expansion'
errors = []
checked = 0
vanilla = zipfile.ZipFile(namespace['VANILLA'])
vanilla_paths = set(vanilla.namelist())

# 验证命名空间内引用是否指向本工程文件，原版引用必须存在于已核对的客户端 JAR。
def require(path):
    global checked
    checked += 1
    if str(path).replace('\\', '/') not in vanilla_paths and not (RES / path).is_file():
        errors.append('Missing resource: ' + str(path))

# JSON 必须是有效 UTF-8，并且拒绝会被普通解码器悄悄覆盖的重复字段。
def object_pairs(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            errors.append('Duplicate JSON key: ' + key)
        value[key] = item
    return value

for path in RES.rglob('*.json'):
    if 'prismatic' not in str(path) and '/lang/' not in path.as_posix():
        continue
    try:
        data = json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=object_pairs)
    except (ValueError, UnicodeError) as error:
        errors.append(f'{path.relative_to(ROOT)}: {error}')
        continue
    relative = path.relative_to(RES).as_posix()
    if '/models/' in relative:
        parent = data.get('parent', '')
        if parent and parent != 'builtin/entity' and not parent.startswith('builtin/'):
            domain, resource = parent.split(':', 1) if ':' in parent else ('minecraft', parent)
            require(f'assets/{domain}/models/{resource}.json')
        for texture in data.get('textures', {}).values():
            if not texture.startswith('#'):
                domain, resource = texture.split(':', 1) if ':' in texture else ('minecraft', texture)
                require(f'assets/{domain}/textures/{resource}.png')
    if '/blockstates/' in relative:
        for model in re.findall(r'"model"\s*:\s*"([^"]+)"', path.read_text(encoding='utf-8')):
            domain, resource = model.split(':', 1) if ':' in model else ('minecraft', model)
            require(f'assets/{domain}/models/{resource}.json')

# 每个玩家可见注册项都有语言、获取入口与资源；实体动画和骨骼引用按真实文件交叉检查。
languages = [json.loads((RES / f'assets/{MOD}/lang/{locale}.json').read_text(encoding='utf-8')) for locale in ['zh_cn', 'en_us']]
for name in BLOCKS:
    for path in [f'assets/{MOD}/blockstates/prismatic_{name}.json', f'assets/{MOD}/models/item/prismatic_{name}.json',
                 f'data/{MOD}/loot_table/blocks/prismatic_{name}.json']:
        require(path)
    for language in languages:
        if f'block.{MOD}.prismatic_{name}' not in language: errors.append('Missing block translation: ' + name)
for name in ITEMS:
    require(f'assets/{MOD}/models/item/prismatic_{name}.json')
    for language in languages:
        for prefix in ['item', 'tooltip']:
            if f'{prefix}.{MOD}.prismatic_{name}' not in language: errors.append('Missing material translation: ' + name)
for name in ENTITIES:
    model = RES / f'assets/{MOD}/geo/prismatic/{name}.geo.json'
    animation = RES / f'assets/{MOD}/animations/prismatic/{name}.animation.json'
    for path in [model, animation, RES / f'assets/{MOD}/textures/entity/prismatic/{name}.png',
                 RES / f'assets/{MOD}/models/item/prismatic_{name}_spawn_egg.json', RES / f'data/{MOD}/loot_table/entities/prismatic_{name}.json']:
        require(path.relative_to(RES))
    if model.is_file() and animation.is_file():
        geometry = json.loads(model.read_text(encoding='utf-8'))['minecraft:geometry'][0]
        bones = {bone['name'] for bone in geometry['bones']}
        animations = json.loads(animation.read_text(encoding='utf-8'))['animations']
        for action in ['idle', 'walk', 'attack', 'special', 'hurt', 'death']:
            key = f'animation.{name}.{action}'
            if key not in animations: errors.append('Missing animation: ' + key)
        for key, value in animations.items():
            for bone in value.get('bones', {}):
                if bone not in bones: errors.append(f'Animation {key} references absent bone {bone}')
        if len(bones) < 4: errors.append('Insufficient authored skeleton: ' + name)

# 对实际 Java 使用的本功能语言键逐一校验，避免新交互只显示技术名称。
for path in (ROOT / 'src/main/java/com/hp_end_expansion/content/prismatic').rglob('*.java'):
    text = path.read_text(encoding='utf-8')
    for key in re.findall(r'Component\.translatable\("([^"]+)"', text):
        if key.endswith('_'):
            continue
        for language in languages:
            if key not in language: errors.append('Missing Java translation: ' + key)

# 可选检查正式产物：资源与源码一致，功能类已打包，开发测试及其混入配置没有泄漏。
package = None
if len(sys.argv) > 1:
    jar_path = Path(sys.argv[1]).resolve()
    with zipfile.ZipFile(jar_path) as archive:
        names = set(archive.namelist())
        forbidden = [name for name in names if name.startswith('com/hp_end_expansion/content/prismatic/test/')
                     or 'prismatic.tests.mixins' in name or name.endswith('prismatic_empty.nbt') or name.startswith('tools/')]
        if forbidden: errors.append('Development files in production JAR: ' + ', '.join(sorted(forbidden)))
        resource_count = 0
        for path in RES.rglob('*'):
            relative = path.relative_to(RES).as_posix()
            if path.is_file() and ('prismatic' in relative or '/lang/' in relative):
                resource_count += 1
                if relative not in names or archive.read(relative) != path.read_bytes():
                    errors.append('Missing or stale packaged resource: ' + relative)
        java_root = ROOT / 'src/main/java'
        classes = list((java_root / 'com/hp_end_expansion/content/prismatic').rglob('*.java'))
        for path in classes:
            relative = path.relative_to(java_root).with_suffix('.class').as_posix()
            if relative not in names: errors.append('Missing production class: ' + relative)
        metadata = archive.read('META-INF/neoforge.mods.toml').decode('utf-8')
        if 'hp_end_expansion.prismatic.mixins.json' not in metadata: errors.append('Production biome mixin not registered')
        if 'prismatic.tests.mixins' in metadata: errors.append('Test mixin registered in production metadata')
        package = {'file': jar_path.name, 'sha256': hashlib.sha256(jar_path.read_bytes()).hexdigest(),
                   'matched_resources': resource_count, 'production_classes': len(classes), 'development_files': forbidden}

report = {'resource_references_checked': checked, 'blocks': len(BLOCKS), 'materials': len(ITEMS), 'creatures': len(ENTITIES),
          'production_jar': package,
          'errors': sorted(set(errors)), 'scope': 'Resource linkage and language audit; runtime and rendered appearance require separate evidence.'}
output = ROOT / 'build/prismatic/resource-audit.json'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps(report, ensure_ascii=False, indent=2))
sys.exit(bool(errors))
