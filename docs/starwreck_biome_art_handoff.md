# 星骸荒原 美术交接

给实现星骸荒原代码的人看。美术资产已经齐全，以下列出每个文件的用途和接入方式。设计本身见 [starwreck_biome_design.md](starwreck_biome_design.md)。

资源根目录：`src/main/resources/assets/hp_end_expansion/`。下文路径都相对于它。

## 1. 文件清单

| 文件 | 尺寸 | 用于 |
|---|---|---|
| `textures/block/starwreck_stone.png` | 16×16 | 星骸岩；也是星苔底面 |
| `textures/block/ember_starwreck_stone.png` | 16×16 | 余烬星骸岩底图 |
| `textures/block/ember_starwreck_stone_glow.png` | 16×16 | 余烬星骸岩发光层，只含熔脉像素，其余透明 |
| `textures/block/star_moss.png` | 16×16 | 星苔顶面 |
| `textures/block/star_moss_side.png` | 16×16 | 星苔侧面，上沿苔边、下部岩石 |
| `textures/block/meteor_ash.png` | 16×16 | 陨灰 |
| `textures/block/star_moss_sprouts.png` | 16×16 | 星苔芽，交叉模型 |
| `textures/block/emberbloom.png` | 16×16 | 余烬花，交叉模型；盆栽共用 |
| `textures/block/small_star_crystal_bud.png` 等 4 张 | 16×16 | 星晶芽三阶段 + 星晶簇，交叉模型 |
| `textures/block/star_crystal_block.png` | 16×16 | 星晶块 |
| `textures/block/starwreck_bricks.png` | 16×16 | 星骸砖；楼梯、台阶、墙共用 |
| `textures/item/star_crystal_shard.png` | 16×16 | 星晶碎片物品 |
| `textures/particle/star_ember_0.png` ～ `_3.png` | 8×8 | 余烬火星粒子，由亮到暗 4 帧 |
| `sounds/music/starwreck_wastes.ogg` | 186 s | 群系背景音乐 |

所有贴图都只用全透明和全不透明两档 alpha，交叉模型类方块用 `cutout` 渲染类型。

## 2. 接入要点

### 2.1 余烬星骸岩发光

发光层和底图 UV 完全对齐。推荐做法：方块模型里放两个元素，第二个元素贴发光层，用 NeoForge 的面数据把亮度拉满。

```json
{
  "parent": "minecraft:block/block",
  "render_type": "minecraft:cutout",
  "textures": {
    "particle": "hp_end_expansion:block/ember_starwreck_stone",
    "base": "hp_end_expansion:block/ember_starwreck_stone",
    "glow": "hp_end_expansion:block/ember_starwreck_stone_glow"
  },
  "elements": [
    {
      "from": [0, 0, 0], "to": [16, 16, 16],
      "faces": {
        "down": {"texture": "#base", "cullface": "down"}, "up": {"texture": "#base", "cullface": "up"},
        "north": {"texture": "#base", "cullface": "north"}, "south": {"texture": "#base", "cullface": "south"},
        "west": {"texture": "#base", "cullface": "west"}, "east": {"texture": "#base", "cullface": "east"}
      }
    },
    {
      "from": [0, 0, 0], "to": [16, 16, 16],
      "neoforge_data": {"block_light": 15, "sky_light": 15, "ambient_occlusion": false},
      "faces": {
        "down": {"texture": "#glow", "cullface": "down"}, "up": {"texture": "#glow", "cullface": "up"},
        "north": {"texture": "#glow", "cullface": "north"}, "south": {"texture": "#glow", "cullface": "south"},
        "west": {"texture": "#glow", "cullface": "west"}, "east": {"texture": "#glow", "cullface": "east"}
      }
    }
  ]
}
```

- `neoforge_data` 的字段名是从 NeoForge 21.1.233 的 `ExtraFaceData` 里读出来的，写法本身没有在游戏里跑过。
- cutout 只丢弃透明像素，不能保证不透明的共面像素没有深度冲突。当前实现把第二个元素轻微外扩为 `from [-0.002,…]`、`to [16.002,…]`，并显式保持 UV 为 0～16；已经客户端检查。
- 不想做发光层的话，直接用底图的 `cube_all` 也可以，只是熔脉在暗处不会亮。

### 2.2 星苔

用 `minecraft:block/cube_bottom_top`：`top` = `star_moss`，`side` = `star_moss_side`，`bottom` = `starwreck_stone`。侧面贴图的苔边在上沿，不要用 `cube_column`，那样底面也会变成苔。

### 2.3 星晶芽与星晶簇

四张都是尖端朝上画的，方块状态按紫水晶的写法，用 `x`、`y` 旋转对应六个朝向，模型用 `minecraft:block/cross`。

### 2.4 余烬火星粒子

粒子描述文件 `particles/star_ember.json` 需要和粒子类型注册一起加：

```json
{
  "textures": [
    "hp_end_expansion:star_ember_0",
    "hp_end_expansion:star_ember_1",
    "hp_end_expansion:star_ember_2",
    "hp_end_expansion:star_ember_3"
  ]
}
```

帧顺序是亮 → 暗，粒子寿命内按年龄选帧（`setSpriteFromAge`）即可做出熄灭效果。贴图已经带了颜色，渲染时不要再染色。

### 2.5 背景音乐

`sounds.json` 需要这一项，必须设 `stream: true`：

```json
"music.starwreck_wastes": {
  "sounds": [{"name": "hp_end_expansion:music/starwreck_wastes", "stream": true}]
}
```

- 原版在末地总是播放末地音乐，群系 JSON 里的 `music` 字段不生效。NeoForge 21.1 有客户端事件 `SelectMusicEvent`，可以在玩家位于本群系时用 `setMusic` 换成这首，不需要 mixin。战斗中（末影龙存活）应保留原版 Boss 音乐。
- 这会改变设计文档第 7 节"背景音乐不改"的结论，已同步更新该节。

## 3. 验证情况

| 项目 | 状态 |
|---|---|
| 贴图尺寸、alpha、调色板 | 已用脚本检查 |
| 方块贴图 3×3 平铺 | 已看预览，无明显接缝 |
| 物品图标原尺寸可读性 | 已在 18 格物品栏背景下对比原有图标 |
| 音乐响度、峰值、编码格式 | 已用 ffmpeg 测量：-20 LUFS，峰值 -5.7 dBFS，Vorbis 44.1 kHz 立体声 |
| 音乐听感 | 未试听，只看了频谱图结构；需要人工听一遍 |
| 游戏内效果 | 已接入并完成服务端生成测试与客户端画面检查，见 [实现记录](starwreck_biome_implementation.md)；音乐听感仍待人工试听 |
| 自然方块手绘重绘（2026-09-30） | 星骸岩、余烬星骸岩（含发光层）、星苔顶/侧、陨灰已逐张手绘替换，文件名、尺寸、模型和发光层规格不变，代码不用改。已核对像素与网格一致、发光层 12 像素和熔脉逐像素对齐、3×3/4×4 平铺无接缝；游戏内观感待用户确认 |

## 4. 生物

三种生物都是 GeckoLib 模型，由 `client/StarwreckEntityRenderers.java` 统一渲染：按实体 ID 取 `geo/<id>.geo.json`、`textures/entity/<id>.png`、`animations/<id>.animation.json`，发光部位由同名 `_glowmask` 贴图给出（`AutoGlowingGeoLayer`）。模型朝 -Z（Blockbench 的北面）为前方。

| 生物 | 贴图 | 碰撞箱 | 动画 |
|---|---|---|---|
| 余烬蛾 `ember_moth` | 32×32 | 0.9 × 0.5 | `fly` 循环 0.3 s；`flee` 循环 0.16 s |
| 余烬甲虫 `ember_beetle` | 64×32 | 0.7 × 0.45 | `idle` 2 s、`walk` 0.5 s 循环；`attack` 0.5 s 一次，0.18 s 颚合拢；`death` 0.9 s 停末帧 |
| 陨壳龟 `meteor_tortoise` | 128×64 | 1.2 × 0.9 | `idle` 3 s、`walk` 1.6 s 循环；`retract` 0.4 s 停末帧；`stomp` 1.2 s 一次，0.7 s 砸地 |
| 余烬鳞粉、余烬 | 16×16 物品图标 | — | — |

骨骼：

- 余烬蛾：`body` 下挂 `head`（`antenna_left/right`）、`abdomen`、前翅 `wing_left/right` 和后翅 `hindwing_left/right`。翅膀是零厚度面片，上下两面分别绘制，下表面偏浅；轴心在翅根，扇翅绕 Z 轴。
- 余烬甲虫：`body` 下挂 `head`（`mandible_left/right`）、`elytron_left/right`、六条腿 `leg_{front,middle,back}_{left,right}`，每条腿带一节 `*_tip` 胫节。鞘翅轴心在前缘中缝，死亡时绕 Z 轴向两侧掀开；行走为三角步态。
- 陨壳龟：`body` 下挂 `shell`（`crystal_1`～`crystal_4`）、`neck` > `head`、`tail`、四条腿 `leg_{front,back}_{left,right}`。四簇星晶是独立骨骼，渲染器按实体同步的剩余晶簇数隐藏 `crystal_(n+1)` 起的骨骼。

贴图：

- 龟壳各面和甲虫鞘翅直接取第 1 节的方块贴图（星骸岩、余烬星骸岩及其发光层、星苔顶/侧），与地形同一套手绘石板和苔团、1 像素 = 1 单位。方块贴图改了以后需要重新运行生成脚本，生物才会跟着变。
- 鳞皮、翅膀、腹部和晶柱是在网格上手绘的。发光像素：余烬蛾的眼斑、腹部环纹和复眼，甲虫的中缝透光点、前胸余烬脉和复眼，陨壳龟的余烬岩熔脉、眼睛和晶柱亮面。

可编辑工程和生成脚本不在仓库里，放在 `D:\AI临时文件\starwreck_creatures`：`ember_moth.bbmodel` 等三个 Blockbench 工程（含动画），生成脚本 `kit.py`、`ember_moth.py`、`ember_beetle.py`、`meteor_tortoise.py`、`items.py`，截图在 `qa\`。改外形或动作时改脚本重新生成，再覆盖资源目录里的文件；Blockbench 重新导出的 geo 与脚本输出逐项一致。
