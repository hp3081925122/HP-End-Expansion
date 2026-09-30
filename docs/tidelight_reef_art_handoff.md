# 潮光礁海 美术交接

给实现潮光礁海代码的人看。方块、物品、粒子贴图和音频已经齐全，以下列出每个文件的用途和接入方式。设计本身见 [tidelight_reef_biome_design.md](tidelight_reef_biome_design.md)，代码接入情况见 [tidelight_reef_implementation.md](tidelight_reef_implementation.md)。生物（灯水母、珍珠寄居蟹、礁鳗、鸣潮巨鲸）的模型与贴图不在本次交付范围内。

资源根目录：`src/main/resources/assets/hp_end_expansion/`。下文路径都相对于它。生成脚本在 `art/tidelight_reef/`，说明见该目录的 README。

## 1. 文件清单

### 1.1 方块贴图（`textures/block/`）

| 文件 | 尺寸 | 用于 |
|---|---|---|
| `reefstone.png` | 16×16 | 礁岩；也是荧藻礁岩底面 |
| `glowkelp_reefstone.png` | 16×16 | 荧藻礁岩顶面底图 |
| `glowkelp_reefstone_side.png` | 16×16 | 荧藻礁岩侧面，上沿藻层向下垂须，下部礁岩 |
| `glowkelp_reefstone_glow.png` + `.mcmeta` | 16×128 | 顶面发光层，8 帧动画，只含藻叶亮部，其余透明 |
| `glowkelp_reefstone_side_glow.png` + `.mcmeta` | 16×128 | 侧面发光层，8 帧动画，只覆盖上沿藻层 |
| `pearl_sand.png` | 16×16 | 珍砂 |
| `tidemarked_reefstone.png` | 16×16 | 潮痕礁岩 |
| `lumen_coral.png` | 16×16 | 光珊瑚，交叉模型 |
| `lumen_coral_block.png` | 16×16 | 光珊瑚块 |
| `lumen_coral_fan.png` | 16×16 | 光珊瑚扇；平放与贴墙共用，物品图标也用它 |
| `tide_whiskers.png` | 16×16 | 潮须草，交叉模型 |
| `lantern_anemone.png` | 16×16 | 灯葵，交叉模型；盆栽共用 |
| `pearl_clam.png` | 16×16 | 珍珠贝，交叉模型，半开贝壳的正视图 |
| `hanging_glowkelp.png` | 16×16 | 垂光藻末端（未结荚） |
| `hanging_glowkelp_pod.png` | 16×16 | 垂光藻末端（`pod=true`） |
| `hanging_glowkelp_plant.png` | 16×16 | 垂光藻藻身，上下无缝 |
| `tide_lantern.png` | 16×16 | 潮灯 |
| `reefstone_bricks.png` | 16×16 | 礁岩砖；楼梯、台阶、墙共用 |
| `chiseled_reefstone.png` | 16×16 | 雕纹礁岩 |
| `pearl_block.png` | 16×16 | 珍珠块 |
| `reef_bone_block.png` / `reef_bone_block_top.png` | 16×16 | 礁化骨块侧面 / 端面（`cube_column`） |

### 1.2 物品图标（`textures/item/`）

| 文件 | 尺寸 | 用于 |
|---|---|---|
| `tide_pearl.png` | 16×16 | 潮汐珍珠 |
| `lumen_coral_branch.png` | 16×16 | 光珊瑚枝 |
| `glowkelp_pod.png` | 16×16 | 光藻荚（同时是垂光藻的方块物品） |
| `tidelight_gel.png` | 16×16 | 潮光胶 |
| `reef_eel_scale.png` | 16×16 | 礁鳗鳞 |
| `tidecall_conch.png` | 16×16 | 鸣潮螺 |
| `whalesong_heart.png` | 16×16 | 鲸歌之心 |

### 1.3 粒子（`textures/particle/`）

| 文件 | 尺寸 | 用于 |
|---|---|---|
| `tide_mote_0.png` ～ `_3.png` | 8×8 | 潮光微粒，由亮到暗 4 帧 |
| `tide_bubble_0.png` ～ `_2.png` | 8×8 | 潮泡：小泡 → 满泡 → 破裂飞溅，3 帧 |

### 1.4 音频（`sounds/`）

| 文件 | 规格 | 用于 |
|---|---|---|
| `music/tidelight_reef.ogg` | 200 s，立体声，-20.0 LUFS，峰值 -5.6 dBFS，1.5 MB | 背景音乐《退潮之歌》 |
| `ambient/tidelight_reef/loop.ogg` | 24 s，立体声，-31.1 LUFS，峰值 -15.3 dBFS | 群系环境循环 |
| `ambient/tidelight_reef/additions1.ogg` | 7.0 s，单声道，-27.0 LUFS | 附加音效：远处鲸鸣 |
| `ambient/tidelight_reef/additions2.ogg` | 4.3 s，单声道，-28.4 LUFS | 附加音效：一串气泡 |
| `ambient/tidelight_reef/additions3.ogg` | 5.5 s，单声道，-27.9 LUFS | 附加音效：玻璃般的三音轻响 |

全部为 Vorbis 44.1 kHz。所有贴图都只用全透明和全不透明两档 alpha。

## 2. 接入要点

### 2.1 荧藻礁岩发光层

做法与余烬星骸岩相同（见 [星骸荒原美术交接 2.1](starwreck_biome_art_handoff.md)）：模型放两个元素，第二个元素贴发光层，用 `neoforge_data` 把亮度拉满。当前 `models/block/glowkelp_reefstone.json` 已按此写好，第二个元素轻微外扩以避免深度冲突，底面不贴发光层。

- 两张发光层是纵向排列的 8 帧条带，`.mcmeta` 为 `frametime 10`、`interpolate true`，一个周期 80 tick（4 秒）。
- 顶面图案是斜向水波，周期 16 像素，四方连续；所有方块同步播放，连成一片就是光波缓慢扫过地面。侧面波纹只沿水平方向推进，和相邻方块顶面的波相位对齐。
- 发光层只点亮底图里藻叶的亮部，底层藻毯和侧面的礁岩保持不亮。不要给底图本身加全亮，否则藻毯会整片发白。
- 2026-09-30 起礁岩、荧藻礁岩顶/侧、珍砂、潮痕礁岩、光珊瑚块改为逐张手绘，文件名、尺寸和发光层动画规格都没变，模型和代码不用改。礁化骨块的侧面和端面同日重绘，仍是 `cube_column`，模型和方块状态不用改。

### 2.2 植物类

- 光珊瑚、潮须草、灯葵、珍珠贝、垂光藻三张：`minecraft:block/cross`，`cutout`。
- 光珊瑚扇：平放用 `minecraft:block/coral_fan`，贴墙用 `minecraft:block/coral_wall_fan`，同一张 `lumen_coral_fan`。贴图按原版珊瑚扇的半扇形构图画，扇根在下沿中间。
- 珍珠贝：贴图是交叉模型用的正视图（张开的贝壳托着珍珠，贝壳下沿贴底），不是立方体 UV 展开。设计文档早期写的"自定义模型"已改。若以后要做立体贝壳，需要另画贴图。
- 垂光藻藻身上下两行像素可以无缝拼接，已在预览 `art/tidelight_reef/preview/hanging_column.png` 里叠 3 段藻身 + 末端检查过。末端（普通 / 结荚）的上沿与藻身下沿对齐。
- 灯葵和结荚末端建议也做全亮面（同 2.1 的做法，或在模型里给整个元素加 `neoforge_data`），否则在暗处只剩剪影。这是可选项，不做也能用。

### 2.3 粒子

粒子描述文件 `particles/tide_mote.json`、`particles/tide_bubble.json` 已存在，帧顺序与文件编号一致。

- 潮光微粒：帧 0 最亮最大，帧 3 只剩一两个暗像素，按年龄选帧（`setSpriteFromAge`）即可做出渐隐。
- 潮泡：帧 0 小泡，帧 1 满泡（左上有高光），帧 2 是破裂时向四角飞散的水点。帧 2 适合只留在寿命最后几 tick。
- 贴图已经带青色，渲染时不要再染色；半透明效果用 `PARTICLE_SHEET_TRANSLUCENT` 配合 alpha 淡出即可。

### 2.4 音频

`sounds.json` 中三项已写好：音乐与环境循环都要 `stream: true`，附加音效是三个变体的普通列表。

- 音乐响度与《余烬之野》一致（-20 LUFS），两首之间切换不会忽大忽小。首尾各有 6 s 淡入、12 s 淡出。
- 环境循环里所有成分都是整数周期，混响用循环卷积，首尾样本差 0.0013，可以直接无缝循环。响度参照原版玄武岩三角洲的环境循环（实测 -31.8 LUFS）。
- 附加音效是单声道点声源，比循环略响、明显低于音乐。群系 JSON 里 `tick_chance` 0.0111 与原版一致，平均约 4.5 秒一次。

## 3. 验证情况

| 项目 | 状态 |
|---|---|
| 贴图尺寸、alpha、调色板 | 已用脚本检查：27 张 16×16（方块 20、物品 7）、2 张 16×128、7 张 8×8，alpha 只有 0 / 255 |
| 方块贴图 3×3 平铺 | 已看预览 `blocks_tiles.png`，礁岩、荧藻、珍砂、珊瑚块无明显接缝 |
| 发光层动画 | 已看逐帧预览 `kelp_glow_frames.png`；游戏内插值效果未看 |
| 物品图标原尺寸可读性 | 已在灰色物品栏背景下检查 `items_slot.png`，礁鳗鳞为此提亮了一档 |
| 资源引用 | 已核对现有模型、粒子描述、`sounds.json` 引用的贴图和音频，缺失 0 个 |
| 音频响度、峰值、编码、循环接缝 | 已用 ffmpeg 测量，数值见 1.4 |
| 音乐与环境音听感 | 未试听，只看了频谱图 `art/tidelight_reef/audio/tidelight_reef_spectrogram.png`；需要人工听一遍 |
| 游戏内效果 | 未看；远处光帘、光波同步、灯葵在暗处的可读性需要进游戏验收 |
