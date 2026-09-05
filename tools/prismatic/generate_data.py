from pathlib import Path
import json
import zipfile

# 数据生成只管理折光荒原条目，语言和原版标签按键合并保留已有内容。
ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / 'src/main/resources'
MOD = 'hp_end_expansion'
VANILLA = Path('C:/Users/30819/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
BLOCKS = {
    'prism_soil': ('风化棱土', 'Prismatic Soil'), 'chalkstone': ('燧白壳岩', 'Chalkstone'),
    'chalkstone_bricks': ('壳岩砖', 'Chalkstone Bricks'), 'chalkstone_stairs': ('壳岩砖楼梯', 'Chalkstone Brick Stairs'),
    'chalkstone_slab': ('壳岩砖台阶', 'Chalkstone Brick Slab'), 'chalkstone_wall': ('壳岩砖墙', 'Chalkstone Brick Wall'),
    'cracked_bricks': ('裂纹壳岩砖', 'Cracked Chalkstone Bricks'), 'chiseled_chalkstone': ('凿纹壳岩', 'Chiseled Chalkstone'),
    'lumen_ore': ('储光矿', 'Lumen Ore'), 'lumen_block': ('蓄光晶块', 'Lumen Crystal Block'),
    'dusk_glass': ('暮玻璃', 'Dusk Glass'), 'prism_log': ('棱冠原木', 'Prismcrown Log'),
    'stripped_prism_log': ('去皮棱冠木', 'Stripped Prismcrown Log'), 'prism_planks': ('棱冠木板', 'Prismcrown Planks'),
    'prism_stairs': ('棱冠木楼梯', 'Prismcrown Stairs'), 'prism_slab': ('棱冠木台阶', 'Prismcrown Slab'),
    'prism_leaves': ('棱冠叶', 'Prismcrown Leaves'), 'prism_lamp': ('折光灯', 'Prismatic Lamp'),
    'survey_marker': ('定线标', 'Survey Marker'), 'regent_altar': ('万相祭台', 'Regent Altar'),
    'prism_grass': ('集光草', 'Prism Grass'), 'mica_reed': ('云母节', 'Mica Reed'),
    'lantern_bloom': ('灯盏花', 'Lantern Bloom'), 'glass_fern': ('玻璃蕨', 'Glass Fern'),
    'shard_cactus': ('碎棱柱', 'Shard Cactus'), 'dusk_bloom': ('暮蕾花', 'Dusk Bloom'),
    'prism_sapling': ('棱冠树苗', 'Prismcrown Sapling')}
ITEMS = {
    'crystal_bud': ('晶芽', 'Crystal Bud'), 'crystal_shard': ('晶屑', 'Crystal Shard'),
    'prism_meal': ('棱壤粉', 'Prism Meal'), 'mica_sheet': ('云母片', 'Mica Sheet'),
    'lumen_dust': ('蓄光尘', 'Lumen Dust'), 'prism_needle': ('棱针', 'Prism Needle'),
    'dusk_bud': ('暮蕾', 'Dusk Bud'), 'dusk_silk': ('暮丝', 'Dusk Silk'),
    'horn_fragment': ('矿角碎片', 'Mineral Horn Fragment'), 'carapace': ('砾甲片', 'Shard Carapace'),
    'fault_sigil': ('裂层印', 'Fault Sigil'), 'mirror_sigil': ('镜面印', 'Mirror Sigil'),
    'lumen_crystal': ('蓄光晶', 'Lumen Crystal'), 'prism_key': ('双印棱钥', 'Twin-Sigil Key'),
    'regent_core': ('万相核心', 'Regent Core'), 'refraction_charm': ('折光护符', 'Refraction Charm')}
ENTITIES = {
    'shardling': ('碎晶虫', 'Shardling'), 'prism_hare': ('棱兔', 'Prism Hare'), 'facet_ram': ('片角羊', 'Facet Ram'),
    'dusk_moth': ('暮翅蛾', 'Dusk Moth'), 'glass_stalker': ('玻行猎兽', 'Glass Stalker'),
    'needle_spitter': ('针冠射手', 'Needle Spitter'), 'shardback': ('砾背兽', 'Shardback'),
    'glare_wisp': ('耀斑灵', 'Glare Wisp'), 'fault_warden': ('裂层卫', 'Fault Warden'),
    'mirror_huntress': ('狩镜者', 'Mirror Huntress'), 'parallax_regent': ('万相冕主', 'Parallax Regent')}
PLANTS = dict(zip(['prism_grass', 'mica_reed', 'lantern_bloom', 'glass_fern', 'shard_cactus', 'dusk_bloom'],
                  ['crystal_bud', 'mica_sheet', 'lumen_dust', 'crystal_shard', 'prism_needle', 'dusk_bud']))

# 所有注册路径统一添加前缀，原版 ID 原样保留。
def rid(short):
    return short if ':' in short else f'{MOD}:prismatic_{short}'

# JSON 输出统一采用无 BOM 的 UTF-8，结果可重复生成。
def write(path, value):
    target = RES / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

# 数据标签只追加本功能内容，不清空共享原版或模组标签。
def tag(registry, name, values):
    target = RES / f'data/minecraft/tags/{registry}/{name}.json'
    data = json.loads(target.read_text(encoding='utf-8')) if target.exists() else {'replace': False, 'values': []}
    for value in values:
        if value not in data['values']:
            data['values'].append(value)
    write(target.relative_to(RES), data)

# 读取当前 1.21.1 原版数据语法，以替换材料名的方式保留工具/附魔语义。
def vanilla_block_loot(name, replacements):
    with zipfile.ZipFile(VANILLA) as archive:
        data = archive.read(f'data/minecraft/loot_table/blocks/{name}.json').decode('utf-8')
    for original, replacement in replacements.items():
        data = data.replace(original, replacement)
    value = json.loads(data)
    value.pop('random_sequence', None)
    return value

# 建筑掉落沿用原版双台阶、精准采集、时运和爆炸衰减行为。
for name in BLOCKS:
    loot = {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
             'entries': [{'type': 'minecraft:item', 'name': rid(name)}]}]}
    if name.endswith('_slab'):
        loot = vanilla_block_loot('oak_slab', {'minecraft:oak_slab': rid(name)})
    elif name == 'lumen_ore':
        loot = vanilla_block_loot('iron_ore', {'minecraft:iron_ore': rid(name), 'minecraft:raw_iron': rid('lumen_crystal')})
    elif name == 'dusk_glass':
        loot = vanilla_block_loot('glass', {'minecraft:glass': rid(name)})
    elif name == 'prism_leaves':
        loot = vanilla_block_loot('oak_leaves', {'minecraft:oak_leaves': rid(name), 'minecraft:oak_sapling': rid('prism_sapling'), 'minecraft:apple': rid('crystal_bud')})
    elif name in PLANTS:
        loot['pools'].append({'rolls': 1, 'conditions': [{'condition': 'minecraft:block_state_property', 'block': rid(name), 'properties': {'age': '3'}}],
                             'entries': [{'type': 'minecraft:item', 'name': rid(PLANTS[name])}]})
    write(f'data/{MOD}/loot_table/blocks/prismatic_{name}.json', loot)

# 每次实体击杀的材料数量由单独池控制，关键精英印记和核心保证一份。
entity_drops = {
    'shardling': [('crystal_shard', 0, 1)], 'prism_hare': [('crystal_bud', 0, 1)],
    'facet_ram': [('horn_fragment', 0, 1)], 'dusk_moth': [('dusk_silk', 0, 1)],
    'glass_stalker': [('crystal_shard', 1, 3)], 'needle_spitter': [('prism_needle', 1, 3)],
    'shardback': [('carapace', 1, 3)], 'glare_wisp': [('lumen_dust', 1, 3)],
    'fault_warden': [('fault_sigil', 1, 1), ('carapace', 3, 6)],
    'mirror_huntress': [('mirror_sigil', 1, 1), ('dusk_silk', 3, 6)],
    'parallax_regent': [('regent_core', 1, 1), ('lumen_crystal', 6, 10)]}
for name, drops in entity_drops.items():
    pools = []
    for item, low, high in drops:
        pools.append({'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': rid(item), 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': low, 'max': high}}]}]})
    write(f'data/{MOD}/loot_table/entities/prismatic_{name}.json', {'type': 'minecraft:entity', 'pools': pools})

# 路站保证种植入口，精英设施只补充材料，不从箱子直接发放挑战印记。
chests = {
    'waystation': [('prism_grass', 2, 4), ('glass_fern', 1, 3), ('crystal_bud', 4, 8), ('prism_meal', 2, 4), ('prism_sapling', 1, 2)],
    'polishing_works': [('mica_reed', 1, 3), ('lantern_bloom', 1, 3), ('lumen_crystal', 2, 4), ('carapace', 1, 3)],
    'broken_observatory': [('dusk_bloom', 1, 3), ('shard_cactus', 1, 3), ('dusk_silk', 2, 4), ('lumen_dust', 2, 4)],
    'prism_court': [('crystal_bud', 6, 10), ('minecraft:golden_carrot', 2, 4), ('minecraft:ender_pearl', 1, 3)]}
for name, drops in chests.items():
    pools = [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': rid(item), 'functions': [
        {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': low, 'max': high}}]}]} for item, low, high in drops]
    write(f'data/{MOD}/loot_table/chests/prismatic_{name}.json', {'type': 'minecraft:chest', 'pools': pools})

# 自动解锁配方可让原版配方书提示制作入口；JEI 直接读取这些标准配方。
def recipe(name, value, unlock):
    write(f'data/{MOD}/recipe/prismatic/{name}.json', value)
    write(f'data/{MOD}/advancement/recipes/prismatic/{name}.json', {
        'parent': 'minecraft:recipes/root', 'criteria': {'material': {'trigger': 'minecraft:inventory_changed',
        'conditions': {'items': [{'items': rid(unlock)}]}}}, 'requirements': [['material']],
        'rewards': {'recipes': [f'{MOD}:prismatic/{name}']}})

# 有形配方用于建筑形状和探索物品，所有输出都采用 1.21.1 的 result.id。
def shaped(name, output, pattern, keys, count=1):
    recipe(name, {'type': 'minecraft:crafting_shaped', 'category': 'misc', 'pattern': pattern,
                 'key': {k: {'item': rid(v)} for k, v in keys.items()}, 'result': {'id': rid(output), 'count': count}}, next(iter(keys.values())))

# 无形配方处理材料回收、育苗和储存块拆分。
def shapeless(name, output, inputs, count=1):
    recipe(name, {'type': 'minecraft:crafting_shapeless', 'category': 'misc', 'ingredients': [{'item': rid(v)} for v in inputs],
                 'result': {'id': rid(output), 'count': count}}, inputs[0])

shaped('chalkstone_bricks', 'chalkstone_bricks', ['##', '##'], {'#': 'chalkstone'}, 4)
for material, prefix in [('chalkstone_bricks', 'chalkstone'), ('prism_planks', 'prism')]:
    shaped(prefix + '_stairs', prefix + '_stairs', ['#  ', '## ', '###'], {'#': material}, 4)
    shaped(prefix + '_slab', prefix + '_slab', ['###'], {'#': material}, 6)
shaped('chalkstone_wall', 'chalkstone_wall', ['###', '###'], {'#': 'chalkstone_bricks'}, 6)
shaped('chiseled_chalkstone', 'chiseled_chalkstone', ['#', '#'], {'#': 'chalkstone_slab'})
for source in ['prism_log', 'stripped_prism_log']:
    shapeless('planks_from_' + source, 'prism_planks', [source], 4)
shaped('sticks', 'minecraft:stick', ['#', '#'], {'#': 'prism_planks'}, 4)
shaped('lumen_block', 'lumen_block', ['###', '###', '###'], {'#': 'lumen_crystal'})
shapeless('lumen_block_unpack', 'lumen_crystal', ['lumen_block'], 9)
shaped('lumen_crystal', 'lumen_crystal', [' D ', 'DSD', ' D '], {'D': 'lumen_dust', 'S': 'crystal_shard'})
shaped('dusk_glass', 'dusk_glass', ['GGG', 'GSG', 'GGG'], {'G': 'minecraft:glass', 'S': 'mica_sheet'}, 8)
shaped('prism_lamp', 'prism_lamp', ['GLG', 'LCL', 'GLG'], {'G': 'dusk_glass', 'L': 'lumen_crystal', 'C': 'regent_core'}, 8)
shaped('survey_marker', 'survey_marker', [' L ', ' C ', 'BBB'], {'L': 'lumen_crystal', 'C': 'mica_sheet', 'B': 'chalkstone_bricks'}, 2)
shaped('regent_altar', 'regent_altar', ['LCL', 'BBB', 'OOO'], {'L': 'lumen_crystal', 'C': 'chiseled_chalkstone', 'B': 'chalkstone_bricks', 'O': 'minecraft:obsidian'})
shaped('prism_key', 'prism_key', ['FCM', ' S '], {'F': 'fault_sigil', 'C': 'lumen_crystal', 'M': 'mirror_sigil', 'S': 'dusk_silk'})
shaped('refraction_charm', 'refraction_charm', [' S ', 'HCH', ' L '], {'S': 'dusk_silk', 'H': 'horn_fragment', 'C': 'regent_core', 'L': 'lumen_crystal'})
shapeless('prism_soil', 'prism_soil', ['minecraft:end_stone', 'prism_meal'], 4)
for plant, produce in PLANTS.items():
    shapeless(plant + '_propagation', plant, [produce, 'prism_meal'])
recipe('cracked_bricks', {'type': 'minecraft:smelting', 'category': 'blocks', 'ingredient': {'item': rid('chalkstone_bricks')},
       'result': {'id': rid('cracked_bricks')}, 'experience': 0.1, 'cookingtime': 200}, 'chalkstone_bricks')
for name, count in [('chalkstone_bricks', 1), ('chalkstone_stairs', 1), ('chalkstone_slab', 2), ('chalkstone_wall', 1), ('chiseled_chalkstone', 1)]:
    recipe(name + '_stonecutting', {'type': 'minecraft:stonecutting', 'ingredient': {'item': rid('chalkstone')},
           'result': {'id': rid(name), 'count': count}}, 'chalkstone')

# 标签保证工具、叶片更新、通用木材配方和楼梯墙体识别正确。
wood = ['prism_log', 'stripped_prism_log', 'prism_planks', 'prism_stairs', 'prism_slab']
stone = [n for n in BLOCKS if n not in wood + ['prism_soil', 'prism_leaves', 'dusk_glass', 'prism_sapling'] and n not in PLANTS]
tag('block', 'mineable/pickaxe', list(map(rid, stone)))
tag('block', 'mineable/axe', list(map(rid, wood)))
tag('block', 'mineable/shovel', [rid('prism_soil')])
tag('block', 'needs_stone_tool', [rid('lumen_ore')])
for registry in ['block', 'item']:
    tag(registry, 'logs', [rid('prism_log'), rid('stripped_prism_log')])
    tag(registry, 'logs_that_burn', [rid('prism_log'), rid('stripped_prism_log')])
    tag(registry, 'planks', [rid('prism_planks')])
    tag(registry, 'leaves', [rid('prism_leaves')])
    tag(registry, 'saplings', [rid('prism_sapling')])
    tag(registry, 'stairs', [rid('chalkstone_stairs'), rid('prism_stairs')])
    tag(registry, 'slabs', [rid('chalkstone_slab'), rid('prism_slab')])
    tag(registry, 'walls', [rid('chalkstone_wall')])
    tag(registry, 'wooden_stairs', [rid('prism_stairs')])
    tag(registry, 'wooden_slabs', [rid('prism_slab')])
write(f'data/neoforge/data_maps/block/strippables.json', {'values': {rid('prism_log'): {'stripped_block': rid('stripped_prism_log')}}})

# 物品说明把首次探索、喂食与仪式条件直接交给玩家。
hints = {
    'crystal_bud': ('集光草成熟后采收。可食用，也可喂棱兔与片角羊。', 'Harvest mature prism grass. Edible; feed prism hares and facet rams.'),
    'crystal_shard': ('喂给碎晶虫，等待消化后获得棱壤粉。', 'Feed a shardling; after digestion it produces prism meal.'),
    'prism_meal': ('右键催熟折光荒原植物；可与采收材料合成新植株。', 'Use on Prismatic Wastes plants to grow them; combine with produce to propagate plants.'),
    'mica_sheet': ('采自成熟云母节，可用于暮玻璃和定线标。', 'Harvested from mature mica reeds; crafts dusk glass and survey markers.'),
    'lumen_dust': ('采自灯盏花，或击败耀斑灵；可凝成蓄光晶。', 'Harvest lantern blooms or defeat glare wisps; condense into lumen crystals.'),
    'prism_needle': ('采自碎棱柱。注意成熟植株的尖刺。', 'Harvested from shard cacti. Mature plants have sharp spines.'),
    'dusk_bud': ('暮蕾花的花苞，可喂给暮翅蛾换取暮丝。', 'A dusk bloom bud; feed a dusk moth to obtain renewable dusk silk.'),
    'dusk_silk': ('暮翅蛾的可再生产物，用于双印棱钥和护符。', 'Renewable from fed dusk moths; used for the twin-sigil key and charm.'),
    'horn_fragment': ('喂食片角羊后空手收取；两次产出间需要等待。', 'Feed a facet ram, then collect with an empty hand. Production has a cooldown.'),
    'carapace': ('砾背兽与裂层卫的甲片，可与骨粉加工为棱壤粉。', 'Armor from shardbacks and fault wardens; process with bone meal into prism meal.'),
    'fault_sigil': ('击败废弃磨晶工坊的裂层卫获得。与镜面印共同制钥。', 'Defeat the fault warden at a polishing works. Combine with a mirror sigil to craft the key.'),
    'mirror_sigil': ('击败断镜观测所的狩镜者获得。与裂层印共同制钥。', 'Defeat the mirror huntress at a broken observatory. Combine with a fault sigil to craft the key.'),
    'lumen_crystal': ('采掘储光矿或凝炼蓄光尘获得，用于建筑与仪式。', 'Mine lumen ore or condense lumen dust; used for building and the summoning ritual.'),
    'prism_key': ('在末地万相庭祭台上右键。成功召唤才消耗；保留南侧空间。', 'Use on an altar in an End prism court. Consumed only on success; keep space south of the altar clear.'),
    'regent_core': ('万相冕主的战利品：制作折光护符，或一批折光灯。', 'Trophy of the Parallax Regent: craft a refraction charm or a batch of prismatic lamps.'),
    'refraction_charm': ('右键获得抗性提升 I，持续 8 秒；冷却 30 秒。', 'Use for Resistance I for 8 seconds. Cooldown: 30 seconds.')}
shapeless('carapace_recycling', 'prism_meal', ['carapace', 'minecraft:bone_meal'], 3)
messages = {
    'prismatic_charm_active': ('折光屏障已展开：8 秒抗性。', 'Refraction barrier active: 8 seconds of resistance.'),
    'prismatic_altar_peaceful': ('和平难度无法唤醒万相冕主，棱钥已保留。', 'The Regent cannot awaken in Peaceful difficulty. Your key has been retained.'),
    'prismatic_product_growing': ('正在凝聚产物，还需约 %s 秒。', 'The product is forming: about %s seconds remaining.'),
    'prismatic_altar_hint': ('双印棱钥：裂层印 + 镜面印 + 蓄光晶 + 暮丝。', 'Twin-sigil key: fault sigil + mirror sigil + lumen crystal + dusk silk.'),
    'prismatic_altar_outside_court': ('这座祭台未连接万相庭。请前往末地的完整庭院。', 'This altar is not connected to a prism court. Find a court in the End.'),
    'prismatic_altar_occupied': ('附近的万相冕主仍然存活。', 'A living Parallax Regent is already nearby.'),
    'prismatic_altar_obstructed': ('召唤空间被阻挡，请清空祭台南侧及上方。', 'The summoning space is obstructed. Clear the area south of and above the altar.')}
for idx, locale in enumerate(['zh_cn', 'en_us']):
    target = RES / f'assets/{MOD}/lang/{locale}.json'
    language = json.loads(target.read_text(encoding='utf-8'))
    for name, labels in BLOCKS.items(): language[f'block.{MOD}.prismatic_{name}'] = labels[idx]
    for name, labels in ITEMS.items(): language[f'item.{MOD}.prismatic_{name}'] = labels[idx]
    for name, labels in ENTITIES.items():
        language[f'entity.{MOD}.prismatic_{name}'] = labels[idx]
        language[f'item.{MOD}.prismatic_{name}_spawn_egg'] = labels[idx] + ('刷怪蛋' if idx == 0 else ' Spawn Egg')
    for name, labels in hints.items(): language[f'tooltip.{MOD}.prismatic_{name}'] = labels[idx]
    for name, labels in messages.items(): language[f'message.{MOD}.{name}'] = labels[idx]
    language[f'entity.{MOD}.prismatic_crystal_needle'] = ('晶针', 'Crystal Needle')[idx]
    language[f'biome.{MOD}.prismatic_wastes'] = ('折光荒原', 'Prismatic Wastes')[idx]
    language[f'itemGroup.{MOD}.prismatic_wastes'] = ('折光荒原', 'Prismatic Wastes')[idx]
    language[f'itemGroup.{MOD}.prismatic_entities'] = ('折光荒原生物', 'Prismatic Wastes Creatures')[idx]
    language[f'tooltip.{MOD}.prismatic_plant'] = ('种在风化棱土或末地石上。成熟后右键采收；棱壤粉可催熟。', 'Plant on prismatic soil or end stone. Use when mature to harvest; prism meal accelerates growth.')[idx]
    language[f'tooltip.{MOD}.prismatic_tree'] = ('种在风化棱土或末地石上，留出冠层空间；可用骨粉或棱壤粉催生。', 'Plant on prismatic soil or end stone with canopy space. Bone meal or prism meal accelerates growth.')[idx]
    write(target.relative_to(RES), language)

print(f'Generated {len(BLOCKS)} block loot tables, {len(ENTITIES)} entity loot tables, 4 chest tables and progression recipes.')
