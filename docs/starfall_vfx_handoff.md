# 星雨特效交接

> 状态（2026-09-30）：正式画面已接入，占位火星已删。裂隙 `StarRiftRenderer`（6 帧撕开 + 紫黑外缘 + 加法热光），陨星 `FallingStarRenderer`（五块星骸岩团块 + 裂缝发光 + 三层尾焰），落地 `StarImpactEntity` / `StarImpactRenderer`（白闪、来路残影、贴地热浪环与不到 2 格的热墙、贴方块顶面的焦痕、滚动碎石），天空 `StarfallSky`（群系内地平线余烬光和零星流星；星雨时天空压暗发红、裂隙方向被照亮、同向流星雨、落地闪光与震屏、雾色转红，约 7 秒退回）。新粒子 `star_debris`、`star_ash`。裂隙与落地音量放大到 32～80 格能听见。贴图脚本在 `D:\AI临时文件\starfall_vfx\build_vfx_textures.py`。玩法数值与流程未改。尚未进游戏实看。

给写裂隙和陨星画面的人。玩法、时间和落点已经接好，不要改那些类里的数值和流程。逐星兽这次不要做。设计见 [starwreck_biome_design.md](starwreck_biome_design.md) 第 7.1 节和第 9.5 节。

现在的样子只够确认事件发生了：裂隙没有模型，每 10 tick 在实体位置放一粒 `star_ember`；陨星是一块旋转的余烬星骸岩；落地再撒一小圈 `star_ember`。这些都是标记，正式画面接上之后删掉。

## 1. 要做的两件东西

1. 裂隙：竖着撕开的天空伤口，不是圆门，也不是裂隙螳螂的 `rift_portal`。
2. 陨星：从裂隙芯里飞出的燃烧石块，带着指向飞行反方向的尾焰，落地时碎开成一圈热浪。

主体用自绘贴图、自定义粒子或特效实体。原版粒子和现有的 `star_ember`、`rift_spark`、`rift_shard` 只能当碎屑，不能铺成裂隙的轮廓或陨星的身体。

## 2. 不要改的玩法

| 常量 | 位置 | 值 | 含义 |
|---|---|---|---|
| `StarRiftEntity.OPEN_TICKS` | 裂隙 | 20 | 张开，1 秒 |
| `FallingStarEntity.FALL_TICKS` | 陨星 | 30 | 飞行，1.5 秒，进度先慢后快（`t * t`） |
| `StarRiftEntity.CLOSE_TICKS` | 裂隙 | 16 | 闭合，0.8 秒 |
| `StarRiftEntity.HEIGHT` | 调度 | 42 | 裂隙比落点高 42 格 |
| `StarRiftEntity.OFFSET` | 调度 | 6 | 裂隙相对落点的水平偏移 |
| `FallingStarEntity.IMPACT_RADIUS` | 陨星 | 4 | 冲击半径 |
| `FallingStarEntity.IMPACT_DAMAGE` | 陨星 | 8 | 冲击伤害 |

一场一个裂隙、一颗陨星。不破坏方块。不生成逐星兽。间隔、选点和存档都在 `StarRain`、`StarRiftEntity`、`FallingStarEntity` 里，画面代码不要再算一遍落点。

裂隙实体 `hp_end_expansion:star_rift`，陨星实体 `hp_end_expansion:falling_star`。两个都 `noSummon`、无碰撞、不存成生物战利品。

## 3. 时间轴

裂隙年龄用 `StarRiftEntity.getAge()`，渲染时用 `visualAge(partialTick)`，不要用 `tickCount`，客户端的 tickCount 对不齐。

| 视觉年龄 | 阶段 | `phase()` | 画面 |
|---|---|---|---|
| 0～20 | 张开 | `OPENING` | 从一条竖缝拉到全宽。进度 `visualAge / 20` |
| 20 | 陨星出现 | 进入 `HOLD` | 陨星实体在这一拍从裂隙坐标生成 |
| 20～50 | 张开保持 | `HOLD` | 裂隙停在全开，陨星沿自己的位置飞走 |
| 50～66 | 闭合 | `CLOSING` | 收成竖缝后消失。进度 `(visualAge - 50) / 16` |
| 66 | 裂隙移除 |  | 实体 `discard`，渲染不用收尾尸体 |

陨星不把年龄同步到客户端。看它的 `position()` 和 `getDeltaMovement()`：位移就是这一拍的飞行方向，尾焰沿位移的反方向拖。`getYRot()` 是水平飞行朝向。服务端曲线是起点到落点的 `t * t` 插值，客户端不要自己重算抛物线。

## 4. 裂隙画面

- 位置：实体坐标就是裂隙中心，不是落点。落点在斜下方。
- 尺寸：全开时大约 7 格高、2 格宽。张开和闭合都从中线向两侧长，不要突然弹出一整块。
- 形状：竖的、边缘参差的撕裂口。中段最亮，上下两端收尖。可以有轻微的左右错位，但整道裂隙要保持竖立，不要转成水平旋涡。
- 颜色：外缘 `#1A1216`、`#2B1E1E`，带紫的黑；内缘 `#B0581C`、`#E08C2A`；芯 `#FFCB5E` 到 `#FFF1C2`。不要用裂隙螳螂那套纯紫传送门。
- 亮度：芯要亮，外缘不要铺满屏幕。玩家通常在 32～64 格外看它，裂隙要在末地天空里认得出，但站在下面时不要遮住整片视野。
- 音效：现在张开时播一声低沉的紫水晶钟（`AMETHYST_BLOCK_CHIME`，音高 0.5）。可以换成更短的撕裂声，不要用末地传送门的长音。

渲染注册在 `StarwreckEntityRenderers`，裂隙目前是 `NoopRenderer`。换成自己的渲染器，并删掉 `StarRiftEntity.tick` 里那粒标记火星。

## 5. 陨星画面

- 身体：大约 1.5 格的不规则星骸岩，表面有余烬裂缝。颜色用星骸岩暗部加余烬亮缝，不要画成圆滑的火球。
- 旋转：可以继续转，但转速要能看出下落，不要转成一团模糊。
- 尾焰：从身体沿 `getDeltaMovement()` 的反方向拉出，长度大约 4～8 格，越远越淡、越散。颜色从芯的 `#FFF1C2` 收到外缘的 `#B0581C`。飞行前 0.3 秒尾焰短，因为这时还慢。
- 飞出裂隙：生成坐标就是裂隙中心。前几 tick 身体应该还卡在亮芯里，再整颗露出来。不要在裂隙外面另外刷一个出生闪光。
- 落地：到达落点的那一拍实体消失。热浪贴地散开，半径对上 4 格的伤害范围，高度不要超过 2 格。碎石沿地面滚，不要炸出烟柱。现有的爆炸音效（`GENERIC_EXPLODE`，音量 0.8，音高 0.75）可以留，也可以换成更闷的坠地声。
- 占位渲染是 `client/FallingStarRenderer.java`，画的是 `ember_starwreck_stone`。替换或改这个类，并删掉 `FallingStarEntity.impact` 里那 12 粒标记火星。

## 6. 现有文件

| 文件 | 作用 |
|---|---|
| `entity/starwreck/StarRain.java` | 计时和选点 |
| `entity/starwreck/StarRiftEntity.java` | 裂隙实体、年龄、阶段 |
| `entity/starwreck/FallingStarEntity.java` | 陨星飞行和冲击 |
| `client/FallingStarRenderer.java` | 陨星占位，要替换 |
| `client/StarwreckEntityRenderers.java` | 两个实体的渲染注册 |
| `registry/StarwreckEntities.java` | `STAR_RIFT`、`FALLING_STAR` |

粒子类型已经有 `star_ember`、`rift_spark`、`rift_shard`，贴图在 `textures/particle/`。新的主体贴图放在 `textures/effect/` 或 `textures/particle/`，沿用第 2 节色板，alpha 只用全透明和全不透明。

## 7. 验收

进星骸荒原后要等 6～8 分钟才有第一场。看三个时刻：裂隙从缝拉到全开、陨星离开裂隙并沿斜线落地、裂隙在陨星落地后收上。再走到落点上确认人会被推开，地面方块还在。裂隙和陨星的轮廓在没有原版粒子的情况下也要能看清。
