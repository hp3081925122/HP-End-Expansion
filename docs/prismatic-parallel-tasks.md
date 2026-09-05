# 折光荒原分批任务与冻结契约

来源：[设计](prismatic-wastes-design.md)、[总任务](prismatic-task-plan.md)。规划技能要求在不共享文件的任务间并行执行。主代理负责集成及三个文档的精确进度回写，子任务仅写自己的进度报告，由主代理串行合入公共表格。

## A 批：基础契约（已冻结）

- 工作根：`E:/模组/发cf的模组/HP End Expansion/1.21.1-neoforge-hp-end-expansion-prismatic`；分支 `feature/prismatic-wastes`。
- Java 根：`com.hp_end_expansion.content.prismatic`；MODID 保持 `hp_end_expansion`；所有注册 ID 为 `prismatic_` 加设计中的短 ID。
- `PrismaticContent.BLOCKS` / `ITEMS` 是独立延迟注册器；对外采用 `public static Block block(String shortId)`、`public static Item item(String shortId)`；仅在注册完成后调用。`public static void register(IEventBus bus)`。
- `PrismaticEntities` 自己负责实体/刷怪蛋/属性/生成限制注册；入口 `public static void register(IEventBus bus)`；结构及祭台通过 `public static EntityType<?> type(String shortId)` 获取类型，只在注册完成后调用。
- 实体资源位置：`assets/hp_end_expansion/geo/prismatic/<id>.geo.json`、`animations/prismatic/<id>.animation.json`、`textures/entity/prismatic/<id>.png`。动画名固定 `animation.<id>.idle`、`.walk`、`.attack`、`.special`、`.hurt`、`.death`；飞行实体 walk 表示移动。基础动作与技能控制器互斥，不每刻重启动画。
- 实体短 ID：`shardling, prism_hare, facet_ram, dusk_moth, glass_stalker, needle_spitter, shardback, glare_wisp, fault_warden, mirror_huntress, parallax_regent`。
- 所有实体可以独立类或有实际复用的基类实现，但不同物种必须有独立行为差异。掉落由数据表提供。实体 AI 可只依赖已冻结 `PrismaticContent.item/block`，不读主代理尚未完成的实现。
- 方块/物品纹理：`textures/block/prismatic/<shortid>.png` / `textures/item/prismatic/<shortid>.png`；方块状态文件 `blockstates/prismatic_<shortid>.json`；方块模型 `models/block/prismatic/<shortid>.json`；物品模型 `models/item/prismatic_<shortid>.json`。
- 七植物使用 `age=0..3` 属性（树苗例外），采集由交互处理；植物模型资源可以按 age 使用 `<shortid>_0..3`。其他状态：log axis、leaves persistent/distance/waterlogged、stairs/slab/wall 原版属性。祭台/灯/标记为单方块无状态。
- 材料短 ID 和 27 个方块短 ID 以设计第 7 节为准。简写 `prism_sapling` 属于方块，没有重复注册物品。实体掉落表路径 `loot_table/entities/prismatic_<id>.json`。
- 本批禁止其他代理编译共享半成品；主代理运行批次 gate。不得提交或推送其他任务文件。需要更改契约先发消息协调并在此记录。

## B 批：独立实现

| 任务 | 状态/负责人 | 独占写入 | 只读 | 映射 |
|---|---|---|---|---|
| PB1 方块植被与基础集成 | ◐ root | PrismaticContent.java、block/、item/、HpEndExpansion.java、build.gradle、docs 公共表格 | 契约、原版源码 | T1 |
| PB2 实体 Java 与客户端接入 | ◐ prismatic_entities | PrismaticEntities.java、entity/、client/、docs/prismatic-entities-report.md | 契约、依赖源码 | T2 |
| PB3 手工视觉资源生成与审查 | ◐ prismatic_assets | assets 下 prismatic 资源、art/prismatic/、tools/prismatic/assets*.py、docs/prismatic-assets-report.md | 契约、设计 | T3 |
| PB4 世界生成独立实现 | ◐ prismatic_worldgen | worldgen/、content/prismatic/mixin/、hp_end_expansion.prismatic.mixins.json、data 下 prismatic 世界生成与群系/结构标签、docs/prismatic-worldgen-report.md | 冻结的 block/item/type 接口、原版源码 | T4 |

Gate B：三者结束后主代理运行 compileJava + processResources，检查全部 11 个实体及 27 个方块资源路径一致。

## C 批：世界与流程集成

主代理集成 B 批世界生成与实体，独占其余资源 data/、语言、配方、战利品、祭台仪式；读取 B 批完成输出。对应 T4/T5。Gate C：数据启动无注册错误、真实结构定位与生成、生态资源闭环、首领召唤。

## D 批：验证、外部分支与模型

T6 验证先完成再处理 T7 外部分支。两任务仍运行时只读查询；外部分支推送成功且状态明确才在独立集成 worktree 合并。最终合并统一末地群系采样，再运行必要验证。随后 T8 使用 Blockbench MCP 检查另两个任务的模型全部资源，修复重叠/缺失/UV/骨骼引用问题并重新备份。

## 进度协议与变更

子代理结束时回写专属报告（事实/API 来源、文件、验证、剩余问题），发给主代理；主代理精确修改设计、总表、此表。文件所有权跨批串行交接，同批不交叉。任何全局契约变更在下面新增一行。

| 日期 | 变更 | 原因 |
|---|---|---|
| 2026-09-06 | 初次冻结 | 独立分支和原版 API 已核验 |
| 2026-09-06 | 世界生成实现提前至 PB4；最终接线与测试保留 C 批 | worldgen 只依赖已冻结的注册访问接口，与另外三项没有实现或文件依赖 |
| 2026-09-06 | 增加 MISC 晶针投射物 `prismatic_crystal_needle` | 复用棱针物品渲染，不增加第十二种生态生物或独立模型 |
