# PB4 世界生成实现与核验记录

日期：2026-09-06。工作分支 `feature/prismatic-wastes`。只处理折光荒原冻结设计，没有读取另两任务的创意或实现。主代理负责统一编译、资源处理和服务端 GameTest；本子任务没有运行 Gradle、提交或推送。

## 已实现接口与资源

- Java 入口：`com.hp_end_expansion.content.prismatic.worldgen.PrismaticWorldgen.register(IEventBus bus)`。
- Mixin 配置：`hp_end_expansion.prismatic.mixins.json`；主代理挂入模组 metadata。
- 正式动态群系：`hp_end_expansion:prismatic_wastes`。
- 静态 Feature 类型与 configured feature：`prismatic_surface`、`prismatic_vegetation`、`prismatic_tree`；前两者还提供 placed feature。树苗既有 TreeGrower 可以直接使用 `prismatic_tree`。
- 正式结构类型与片段类型：`hp_end_expansion:prismatic_ruin`；四个动态结构 ID 保持 `prismatic_waystation`、`prismatic_polishing_works`、`prismatic_broken_observatory`、`prismatic_prism_court`。
- 结构战利品引用：`hp_end_expansion:chests/prismatic_<上述短ID>`，例如 `hp_end_expansion:chests/prismatic_waystation`。战利品本身由主代理生成。
- 正式统一结构集：`hp_end_expansion:prismatic_ruins`，随机分布 spacing 12 / separation 5，四种布局权重 4 / 2 / 2 / 1。一个候选只选择一种设施，避免独立结构集造成重叠。权重是配置事实，不代表实测出现频率；地形拒绝与候选回退会改变实际分布。

## 群系采样与地表

`TheEndBiomeSource.CODEC` 在原版编解码基础上通过 `RegistryOps.retrieveElement` 读取同一世界的荒原 Holder。Holder 保存在各个 source 实例中，不保存全局静态注册表对象。不注入原版 `create(HolderGetter)`：当前版本 `RegistrySetBuilder.UniversalLookup.get` 会创建未绑定 Holder，Optional 返回值不能用作无副作用的存在性检查。只在正式运行时 codec 中绑定数据包群系，并输出英文 debug 记录。`collectPossibleBiomes` 追加荒原，支持原版群系定位和特征排序。

采样只在原版返回高地/中地之后进行，以 384 格尺度平滑散列噪声大于 0.56 的区域替换为荒原。中央末地、小岛、贫瘠地以及已返回的其他自定义群系不会被改写。噪声不访问世界或区块；坐标图样固定，岛屿本身仍随原版种子变化。最终多分支集成时，仍需由主代理统一各群系取样优先级并实际验证共存。

地表特征在 RAW_GENERATION，逐柱只处理当前 16×16 区块的天然末地石：上方三层转壳岩，28 格尺度噪声大于 0.37 的表层转棱土。矿脉在表面以下 1—14 格生成 2—3 格竖向小脉，极少量裸露矿帮助初次探索。不会改整岛轮廓或产生水体。植被位于 VEGETAL_DECORATION，在结构之后运行，不能长到砖地板上。

## 植被与自然生态入口

- 每区块 42 次有限植株位置尝试，年龄为 1—3，只在棱土且 `canSurvive` 成立时放置。
- 基础选择权重：集光草 4、云母节 2、灯盏花 2、玻璃蕨 2、碎棱柱 1、暮蕾花 1；高集光带增加暮蕾花选择机会。
- 每区块最多两次树尝试，每次有 1/3 概率进入生长；树心限制在当前区块 4—11 格，树冠不会跨区块。
- 棱冠木树干高 4—6 格，顶端双向横枝，扁冠 7×5 下层和 5×5 上层并裁掉角点。先完整检查空间再放置；天然叶 `persistent=false`，初始 distance 按到真实枝干的距离计算，保持长树时稳定、砍树后可腐烂。主代理已确认原木进入 `minecraft:logs`。
- 四种动物为 CREATURE，四种普通怪物为 MONSTER；两精英与 Boss 不进入群系自然刷怪表。实体代理已确认飞行物种可在棱土出生后进入低空行为，不依赖草方块。

## 建筑布局、地基和持久化

| 结构 | 占地 | 主要空间 |
|---|---|---|
| 折光路站 | 13×11 | 四根木柱和单层雨棚、成熟种植槽、工作台、定线标、补给箱 |
| 废弃磨晶工坊 | 25×23 | 三格外墙、南北入口及侧门、四组实心柱、后侧料棚、磨石、矿料堆；中央生成一个裂层卫 |
| 断镜观测所 | 23×23 | 两格外墙、侧面二层台与阶梯、断开的高镜框、玻璃残片及完整掩体；中央生成一个狩镜者 |
| 万相庭 | 33×33 | 无顶、两格边墙、南北五格入口、四根 3×3 实心遮挡柱、嵌地光环、中心祭台 |

地形筛选先做低成本群系网格检查，再按原版噪声单元批量计算全部占地及外侧两格的每一柱高度。`PrismaticTerrainSampler` 复用同一 NoiseChunk 插值缓存，使用与原版 iterateNoiseColumn 一致的噪声设置、流体选择、Y/X/Z 插值和高度谓词。最低地面必须 ≥48；路站高差 ≤2，其他设施 ≤8；每一柱均要求三格连续末地石厚度。不读取/预加载远区块。地板选最高地面，完整八格地基穿入天然地面；入口高差在预检时冻结入 NBT，放置阶段不读取远处高度图。万相庭最大预检范围为 37×37，性能须以测试日志中的真实计时判断。

片段包围盒从地板下八格延伸至地板上十一格，所有体积写入先裁切到当前 chunk bounding box。`postProcess` 与额外存档写入同步，`CompletedChunks` 位图记录各区块完成，`ChestPlaced` 和 `ElitePlaced` 保存单次奖励状态。序列化还保存 `Layout`、`NorthRise`、`SouthRise`。箱子使用原版 `createChest` 写入 LootTable key，不直接填物品；精英通过冻结实体入口取得类型，设置 STRUCTURE 来源与持久化，每设施只生成一个。

祭台位于庭院地板上方一格的正中心。中心偏南四格为 Boss 出生中心，覆盖周围 7×7、向上五格的净空；四柱中心在庭院中心的 X/Z ±10 格。庭院有实心边墙，外围也通过地形预检。自然设施统一使用 `lumen_block` 照明，不放置需要 Boss 核心制作的 `prism_lamp`。

## 已确认的同版本源码

源码根：`E:/模组/mc源码/1.21.1-neoforge-21.1.233-minecraft-sources/src` 与对应 NeoForge `-sources/src`。Minecraft MCP `smart_search` 工具本次未暴露，未伪称调用，直接读取以下当前版本源码。

- `net/minecraft/world/level/biome/TheEndBiomeSource.java`：CODEC、create、collectPossibleBiomes、getNoiseBiome 与中央/高地/中地阈值。
- `net/minecraft/resources/RegistryOps.java`、`world/level/biome/BiomeSource.java`：上下文 Holder 获取、possibleBiomes 延迟缓存及定位路径。
- `world/level/levelgen/feature/Feature.java`、FeaturePlaceContext、NoneFeatureConfiguration、placement/PlacedFeature、BiomeFilter：特征放置入口与数据格式。
- `world/level/block/LeavesBlock.java`、RotatedPillarBlock 和 BlockStateProperties：叶距/自然腐烂属性、原木轴向与 age3。
- `world/level/levelgen/structure/Structure.java`、StructureSet、StructurePiece、StructureStart、StructurePieceType、StructurePieceSerializationContext：结构编解码、生成上下文、包围盒、箱子与片段保存。
- `world/level/levelgen/structure/structures/SwampHutPiece.java`：原版分块驻守生物生成和布尔存档用法。
- `world/level/chunk/ChunkGenerator.java`、`world/level/NoiseColumn.java`：无区块加载的高度和地层列查询。
- `world/level/levelgen/structure/placement/RandomSpreadStructurePlacement.java`：spacing/separation 校验与真实候选网格。
- `net/neoforged/neoforge/registries/DeferredRegister.java`：延迟类型注册。
- `gametest/framework/GameTest.java`、GameTestHelper 及 ServerLevel：独立 GameTest 验证入口与真实末地来源。

## 最终服务端验证事实

主代理第六轮在新测试存档中完成正式编译与 GameTest 启动。日志 `build/prismatic/gametest-sixth.log` 的 01:15:57 明确记录 `All 19 required tests passed :)`，进程正常保存并关闭。19 项中包含本子任务的 8 项世界生成测试和实体代理提供的天然祭台辅助验证。此前的失败没有算作通过。

测试文件：`tools/prismatic/gametest/java/com/hp_end_expansion/content/prismatic/test/PrismaticWorldgenTests.java`。

- 三种种子、四片跨噪声单元区域共 300 列高度与原版逐柱结果精确一致；另有 48 列三格承重厚度与原版完整 NoiseColumn 一致，覆盖负坐标、中央岛、边缘和外岛。
- 真实末地 source 的 possibleBiomes、CODEC 往返、中央岛保留，以及四象限荒原/原版陆地/虚空小岛共存通过。
- 四个动态结构 CODEC、统一结构集引用与真实噪声候选可接受性通过。候选测试用于检查预检规则，实际被选择的布局由下面的原版 locate 流程证明。
- 使用 `ChunkGenerator.findNearestMapStructure` 从 (4096,64,4096) 搜索，限定 12 个结构网格环，分别定位实际被统一结构集选中的全部四类设施；完整加载占地后，结构管理器、真实箱子和天然祭台均存在。
- 四个布局按区块放置、片段存档重载与重复放置幂等通过；两种精英分别恰好一个；庭院祭台、四根实心柱及 7×7×5 出生净空通过。
- 天然庭院通过真实末地 FakePlayer 和正常物品使用流程验证：3 把钥匙成功召唤后剩 2 把；重复点击、和平难度和空间阻挡都保留钥匙；Boss 巢点和持久化状态正确。辅助类为实体代理负责的 `PrismaticAltarChecks`。

### 真实自然定位结果

以下为 seed=0 新测试存档的直接日志事实，定位坐标是原版起始区块的 locate 位置，Y=0 不代表建筑地板高度。实际地板高度以包围盒和方块检查为准。

| 实际布局 | locate 起点 X/Z | 实际地板 Y | 覆盖区块数 | 原版 locate 耗时 | 定位、完整加载与检查总耗时 |
|---|---:|---:|---:|---:|---:|
| 折光路站 | 3680 / 3312 | 62 | 1 | 295.314 ms | 661.4457 ms |
| 废弃磨晶工坊 | 3328 / 4464 | 62 | 9 | 149.003 ms | 740.1518 ms |
| 断镜观测所 | 3712 / 4624 | 59 | 9 | 37.8596 ms | 424.8082 ms |
| 万相庭 | 2912 / 5424 | 63 | 9 | 1000.0604 ms | 1566.1889 ms |

庭院祭台为 (2920,64,5432)，Boss 出生与巢点为 (2920,65,5436)。庭院总耗时还包含完整仪式测试。覆盖区块数是建筑包围盒覆盖数量；原版完整生成还会按正常依赖规则处理邻区块。上述耗时是此次有界搜索的测量值，不作为所有种子、硬件或远距离搜索的固定保证，也不用于推断长期权重分布。

### 地形预检性能

第六轮对照：300 列批量采样共 48.7892 ms，原版逐柱高度查询共 305.6236 ms。三个种子的结果精确一致是接受优化的前提。

| 结构 | 原逐柱实现最慢单次 | 批量实现最慢单次 |
|---|---:|---:|
| 路站 | 453.8304 ms | 7.8341 ms |
| 工坊 | 1014.5182 ms | 19.9588 ms |
| 观测所 | 943.7443 ms | 19.7647 ms |
| 庭院 | 1741.4614 ms | 54.675 ms |

这张表测量一次 `Structure.generate`（包括完整地形预检和构造片段），原值来自第三轮，优化后值来自第六轮。原版 locate 会检查多个候选、加载结构起点和区块，其整体耗时独立列在上一张表中。实现仍检查全部 37×37 范围，并把原先九个承重采样强化为每柱三格连续末地石；没有以降低检查密度换取耗时下降。

## 发现并修复的问题

1. 第二轮 `RegistrySetBuilder.UniversalLookup.get` 会调用 getOrCreate，Optional 不是无副作用的存在性查询。原 create 注入制造未绑定荒原 Holder，导致 bootstrap 失败；已完全删除，只保留运行时正式 CODEC 绑定和英文 debug。不能把该轮 Gradle exit 0 当作服务器成功启动。
2. 原逐柱预检反复构造 NoiseChunk，庭院一次约 1.7 秒。根据 `NoiseBasedChunkGenerator.iterateNoiseColumn`、NoiseSettings 和 NoiseChunk 源码，改为每个原版噪声单元只构造一次缓存，保持相同流体与插值规则，经过多种子精确对照。
3. `DensityFunctions.BeardifierMarker` 为 protected；其公开 `BeardifierOrMarker.CODEC` 是原版标记的 unit codec。当前通过公开 codec 一次解析取得原版单例，不使用反射、访问变换或复制实现。
4. 第五轮真实定位未找到结构，源码确认 `GameTestServer.WORLD_OPTIONS` 固定关闭结构，`ChunkStatusTasks` 因此跳过真正起点生成。仅在显式测试源码集中新增 `PrismaticGameTestServerMixin`，通过 `WorldOptions.withStructures(true)` 开启正常结构流水线，保留原版种子和 End noise 设置；正式源码与分布不因此修改。
5. 四个独立结构集会允许设施碰撞，已统一为带权重的单个结构集；四个独立 structure registry ID 保留。
6. 自然照明已统一为 lumen_block，避免提前获得需要 Boss 核心制作的 prism_lamp。

测试环境代码位于 `tools/prismatic/gametest`，主代理只在 `-PprismaticTests` 时加入源码、资源和测试 Mixin metadata；正式构建将 clean 后不带参数重新构建，检查 JAR 中不包含测试类、模板、配置及 metadata 引用。该正式 JAR 排除检查、提交和推送由主代理接续。

## 交接范围

PB4 的主代码、数据、八项 GameTest、测试环境修正与本报告均已交付并通过当前服务端 gate。本子任务未运行 Gradle、未提交或推送。后续由主代理完成正式产物核验、合并其他分支后的多群系共存验证与远程备份。

客户端未运行，所以不宣称建筑视觉、自然景观构图、长期生态刷新和平衡体验已完成实机观察。单独的 `/locate biome` 命令尚未在此子任务执行；群系已通过真实 source 的 possibleBiomes、四象限采样和存档 CODEC 测试，主代理可在最终集成测试补充命令级观察。其他两分支与 Blockbench 模型检查不属于 PB4，本任务未参考或修改其内容。
