# PB3 折光荒原资源交付报告

日期：2026-09-06。独立工作目录 `1.21.1-neoforge-hp-end-expansion-prismatic`，分支 `feature/prismatic-wastes`。本批只修改折光荒原的视觉资源、制作脚本、可编辑工程和本报告；未修改 Java、语言、data 或公共进度文档，未提交、未推送、未运行 Gradle。

## 完成资源

| 实体 | 骨骼 / 立方体 | 独立轮廓与活动部件 | 纹理 |
|---|---:|---|---:|
| 碎晶虫 `shardling` | 14 / 19 | 扁平三片甲、六足错相、双触角，背甲储光线 | 128×128 |
| 棱兔 `prism_hare` | 10 / 12 | 高耳、长后肢、短前肢、独立短尾，后肢同步弹跳 | 128×128 |
| 片角羊 `facet_ram` | 10 / 17 | 厚肩低头、双段扁片弯角、四足对角步态 | 128×128 |
| 暮翅蛾 `dusk_moth` | 10 / 9 | 四片分轴翅、细腹、双触须，翅缘透明裁形 | 128×128 |
| 玻行猎兽 `glass_stalker` | 10 / 14 | 低伏长躯、下颌、四足、两段连续长尾 | 128×128 |
| 针冠射手 `needle_spitter` | 13 / 14 | 三足干茎体、前向喷口、五根分轴冠针 | 128×128 |
| 砾背兽 `shardback` | 8 / 9 | 扁宽重甲、四短足，整块背甲抬合 | 128×128 |
| 耀斑灵 `glare_wisp` | 5 / 8 | 底部开口环、悬核、双游离辉点，环可旋转扩张 | 128×128 |
| 裂层卫 `fault_warden` | 11 / 24 | 六肢低躯、宽前足、不对称断脊、独立背甲与下颌 | 256×256 |
| 狩镜者 `mirror_huntress` | 12 / 20 | 反关节高腿、双节镰臂、倾斜开放镜架 | 128×128 |
| 万相冕主 `parallax_regent` | 16 / 26 | 宽前躯窄后躯、四足、四片冠架、悬核、双段尾 | 256×256 |

共 119 根骨骼、172 个立方体、1032 个独立面。每个实体具有 `idle / walk / attack / special / hurt / death` 六种动画，共 66 个动画。没有复用其他任务模型或把同一几何换色作为另一物种。

动作时间遵循 PB2 冻结契约：`attack` 2.0 秒，在 1.0 秒打击；`special` 2.4 秒，在 1.2 秒打击。关键姿态分别安排蓄势、短暂停顿、快速打击与收招。`death` 1.0 秒并保持末帧，与 PB2 的 20 tick 死亡停留匹配。蛾的 `walk` 为移动扇翅，光灵的 `walk` 为倾斜漂移。片角羊四足与六足兽的步态按左右、前后位置错相。

按 PB2 新增的“产物成熟可观察”需求，提供三个局部 `*_glowmask.png`：碎晶虫背甲、片角羊角部、暮翅蛾腹部；尺寸与基础图一致，透明背景，仅少量器官像素发光。实际阶段渲染开关由 PB2 实现。

方块资源完整覆盖 20 个基础方块和 7 个植被，共 27 个 blockstate。六种可成熟植物各有 `age=0..3` 的四种独立株型纹理，树苗使用单独株型；原木保留三轴状态，楼梯包括内外角，台阶包括上下及双层，墙包括低连接、高连接和柱。暮玻璃显式使用 `minecraft:translucent`，叶片使用 `minecraft:cutout_mipped`，植物使用 `minecraft:cutout`。

16 个材料均有独立图标和物品模型；11 个刷怪蛋使用原版可染色刷怪蛋模板，颜色由实体注册提供。完整素材总览在 [方块与材料图册](../art/prismatic/previews/block-item-contact-sheet.png)。

## 文件与来源

- 正式实体：`assets/hp_end_expansion/geo/prismatic/`、`animations/prismatic/`、`textures/entity/prismatic/`。
- 正式方块与物品：`blockstates/prismatic_*.json`、`models/block/prismatic/`、`models/item/prismatic_*.json`、`textures/block/prismatic/`、`textures/item/prismatic/`。
- [实体制作脚本](../tools/prismatic/assets_entities.py)、[方块材料脚本](../tools/prismatic/assets_blocks.py)、[检查与预览脚本](../tools/prismatic/assets_review.py)。
- [实体总览](../art/prismatic/previews/entity-contact-sheet.png)；每个实体各有 `*-turnaround.png` 和 `*-actions.png`。前者为前方、斜前、斜后和特殊攻击蓄力；后者是六类动作各七个时间点，共 462 张正式贴图姿势帧。
- `art/prismatic/models/` 中保存 11 份可编辑 `.bbmodel`；内嵌 PNG 与正式 PNG 字节一致，包含独立面 UV、骨骼树和全部关键帧。
- `art/prismatic/uv/` 保存每一面的位置、像素尺寸、材质区及 1 像素留边记录。

已读项目 `gradle.properties` 确认 Minecraft 1.21.1、NeoForge 21.1.233；遵循文档冻结的 GeckoLib 4.9.2 资源路径和命名。仅使用项目已有资源头核对 geo 1.12.0、animation 1.8.0 格式，不取用其造型。楼梯、台阶和墙状态从本机 **Minecraft 1.21.1** `minecraft-client.jar` 的原版 blockstate 读取并替换资源名，脚本检查其 `version.json` 确为 1.21.1。没有参考另外两个任务的设计或模型，也没有联网寻找美术参考。

像素图由按部位写定的确定性绘图脚本绘制，不使用 AI UV。壳甲有分层接缝，矿质有斜面高光，毛皮有纵向簇纹，翅膜有支脉，眼部和核心单独绘制。未用大面积纯色图块替代正式材质。

## 验证事实

最终执行三个 Python 脚本完成生成与检查；运行时使用本机已安装的 Python、Pillow 和 NumPy。检查脚本输出：

```text
{"status": "PASS", "entities": 11, "errors": []}
```

直接证据：[validation.json](../art/prismatic/validation.json)。覆盖范围如下：

- 11 个模型骨骼名唯一、父骨存在，172 个立方体不存在完全重复几何；11 份几何拓扑指纹互不相同。
- 1032 个面全部具备完整 UV；没有越界、矩形重叠或缺失面，图案留有至少 1 像素空边。较大面至少三档有效色阶。蛾的 16 个窄翅边故意透明，正反翼面仍有有效像素。
- 对实际骨骼和立方体变换后的平面进行相交检测，11 模型**静态同向共面重叠为零**。首次视觉检查发现的肩甲、头部、足端闪烁风险已逐处调整体积边界，并重新渲染；正常关节体积嵌入予以保留。
- 每份动画名称、骨骼引用和关键帧时段有效；每个动作采样 13 次，共 858 个姿势检查，没有非有限矩阵或消失几何。
- 所有自有模型、贴图引用真实存在；原版父模型引用在同版本客户端 JAR 中存在。六种植物均有四个不同的年龄纹理。
- 三个产物遮罩非空、尺寸一致，发光像素不超过纹理面积的 5%；工程内嵌贴图与正式 PNG 字节一致。
- 已目视检查全部实体总览、材质总览，以及兔、蛾、两精英和 Boss 的多视角/动作图；未发现静态模型缺失或检查后残留的共面闪烁纹样。
- `git diff --check` 未报告空白错误。

## 验证边界与后续集成

本批的预览是从正式 JSON/PNG 读取的离线软件渲染，验证的是几何、UV、材质和数字关键帧；它不是 GeckoLib 实际渲染器，也不是 Blockbench 截图。当前子任务未暴露 Blockbench MCP 工具，没有声称操作过 Blockbench。`.bbmodel` 已导出但尚未在实际 Blockbench 中导入验证。

root 负责 Gate B 的 `compileJava + processResources` 与最终客户端观察：实体资源是否被实际加载、攻击动作与服务端伤害帧是否同步、透明翅/叶/玻璃排序、成熟器官开关、脚部接地以及碰撞体和长尾/翅/冠的尺度关系。特别是 Boss 的几何全包围约为宽 2.303、高 3.014、长 3.827 格，PB2 初始方形碰撞宽为 3.6 格；已把实际尺寸发给 PB2，建议实机检查侧向碰撞留白，而非仅按 compile 结果确认尺度合适。

本批要求的资源没有缺项。客户端实测、Blockbench 实际导入及最终合并后的其他任务模型审查仍属于 root 的后续验证，不能由本报告的 PASS 代替。
