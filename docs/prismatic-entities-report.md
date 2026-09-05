# PB2 折光实体实现报告

日期：2026-09-06。工作目录：`1.21.1-neoforge-hp-end-expansion-prismatic`，分支 `feature/prismatic-wastes`。本任务未提交或推送 Git，未修改另外两个外部任务。

## 已交付

- `src/main/java/com/hp_end_expansion/content/prismatic/PrismaticEntities.java`：11 个生物类型、11 个刷怪蛋、属性、自然生成限制、类型查询；辅助 `prismatic_crystal_needle` 是第 12 个实体注册项，属于 MISC 投射物，不是第 12 种生物。
- `entity/PrismaticAnimated.java`、`PrismaticAnimal.java`、`PrismaticMonster.java`：共享动作、生态存档和服务端战斗状态机。
- `entity/Shardling.java`、`PrismHare.java`、`FacetRam.java`、`DuskMoth.java`：四种生态动物。
- `entity/GlassStalker.java`、`NeedleSpitter.java`、`Shardback.java`、`GlareWisp.java`：四种普通怪物。
- `entity/FaultWarden.java`、`MirrorHuntress.java`、`ParallaxRegent.java`、`CrystalNeedle.java`：精英、首领和真实晶针。
- `client/PrismaticClient.java`、`PrismaticEntityModel.java`、`PrismaticEntityRenderer.java`：客户端侧注册、精确资源路径、成熟器官发光与稳定技能几何。
- `tools/prismatic/gametest/java/com/hp_end_expansion/content/prismatic/test/PrismaticEntityTests.java`：7 项服务端行为测试，由主任务授权扩展独占文件范围。
- 同目录 `PrismaticAltarChecks.java`：由真实末地自然庭院测试调用的祭台验收辅助类；使用当前 NeoForge FakePlayer 与正式服务端右键交互，不手工伪造庭院归属。

公共入口是 `PrismaticEntities.register(IEventBus)`；客户端 `PrismaticClient` 自行通过 CLIENT 事件订阅接入。结构可用 `PrismaticEntities.type(shortId)`；首领提供 `setNest(BlockPos)`。祭台按 `moveTo → finalizeSpawn → addFreshEntity` 调用即可正确记巢点，`/summon` 在第一个服务端 tick 补齐巢点。

## 生态行为与产量

| 物种 | 已实现差异 | 单次生产等待 | 采收后冷却 |
|---|---|---:|---:|
| 碎晶虫 | 晶屑引诱/喂食；受伤逃跑；六格内每 200 tick 最多处理一堆地面晶屑，拾取一个晶屑进入同一生产槽 | 100 tick | 1200 tick |
| 棱兔 | 晶芽引诱、原版繁殖与幼体跟随；移动时短跳；受伤逃跑 | 无生产槽 | 原版繁殖冷却 |
| 片角羊 | 晶芽繁殖；喂食后空手取矿角；中立，受击低头 20 tick 后顶击，20 tick 收招；最多反击 300 tick | 200 tick | 6000 tick |
| 暮翅蛾 | 暮蕾引诱/喂食；原版 FlyingMoveControl/FlyingPathNavigation 低空寻路，周围花朵搜索每 40 tick 最多 16 个候选 | 200 tick | 3600 tick |

三种产物统一使用 `PrismaticProductCooldown`、`PrismaticProductReadyTicks`、`PrismaticProductFed` 存档字段。自主觅食和玩家喂食不能绕过同一个产物槽，不会自行生成无限掉落实体。只有已加载实体推进计时，空手采收后再计冷却。成功互动及繁殖幼体设置持久化。

产物阶段同步为 0（未喂/冷却）、1（成长）、2（可采）。成长阶段客户端每 30 tick 一粒微光；成熟阶段分别显示背甲储光线、角尖与蛾腹暮丝的局部发光。使用资产任务交付的三张 `_glowmask.png`。这里直接使用 `GeoRenderLayer + RenderType.eyes`，没有调用会改写基础纹理的 AutoGlowingTexture，避免关闭成熟层时器官表面缺失。

真实捕食关系补充：玻行猎兽以低于玩家和反击目标的优先级捕食棱兔。其他折光怪物仍不伤害生态动物。此项应回写设计食物链。

所有自定义生态移动/觅食和片角羊反击均尊重 `NoAI`；基础产物计时仍运行，使静态饲养实体不丢产物进度。

## 战斗、同步与表现

基础攻击动作固定 40 tick，第 20 tick 命中；特殊动作固定 48 tick，第 24 tick 命中，与资产任务的 2.0/2.4 秒动画对齐。死亡动作 1 秒，与原版 20 tick 移除时机一致。统一 main 控制器令技能动作覆盖 idle/walk，不每 tick 重启动画；受伤动作不会打断正在播放的敌人攻击前摇。

技能开始一次性同步类型、是否特殊、世界起始时间、锁定偏航和俯仰。服务器结算伤害，客户端用同一时间计算进度。重载取消半段攻击并保存冷却，至少保留 20 tick 安全间隔。

- 猎兽直线扑击；裂层卫交替正面重击/冲锋；狩镜者交替三针扇射/突进。冲锋仅在 8 tick 有效位移窗口处理当前碰撞盒命中，有同次攻击去重，撞墙或前方断崖停止。
- 射手发出实体晶针，投射物沿锁定方向飞行、与实体/方块碰撞，80 tick 后自动消失。狩镜者固定三条方向，不随机散射或隔墙命中。
- 砾背兽抬甲范围震击；耀斑灵低空蓄亮脉冲。范围攻击检查距离、朝向（横扫）和视线。
- 首领 480 生命、10 基础伤害。60% 和 25% 生命阈值单向推进阶段；阶段、巢点、返巢标志、失去目标计时和连续棱线次数保存。阶段二双线、阶段三三线均逐条完整前摇和收招。
- 首领离巢 38 格、目标离巢 42 格或失去视线/目标 600 tick 后返巢，不回血，不破坏建筑，不改玩家视角。血条只对实际追踪实体的玩家显示。
- `beamEnd()` 用方块碰撞截断射线；伤害使用同一末端与目标 AABB，追加视线检查。放置新的完整掩体能阻挡已经锁定的光束。

视觉载体已经是 renderer 程序化几何：双交叉光束、扇形定线、冲锋提示和最多 48 段的环/弧。命中粒子每次仅 12 粒，阶段变化 30 粒，非战斗不发技能粒子。技能状态扩大视锥包围盒以覆盖本体外的光束。没有仅靠粒子冒充最终光束的未完成项。

资产任务测得首领完整模型为宽 2.303 × 高 3.014 × 长 3.827 格。为减少模型外空命中区域，首领碰撞体从最初 3.6 × 3.8 改为 3.0 × 3.1，模型仍是 16 模型单位=1 格。原版水平碰撞体是正方形，这是长躯体的折中；玩家视角下的贴身命中体验仍须客户端实测。

## 当前版本 API 证据

工程 `gradle.properties` 与 `build.gradle` 确认 Minecraft 1.21.1、NeoForge 21.1.233、Java 21、GeckoLib 4.9.2。未调用未暴露的源码索引 MCP。

读取的原版源码根：`E:/模组/mc源码/1.21.1-neoforge-21.1.233-minecraft-sources/src`。

- `net/minecraft/world/entity/animal/Animal.java`：`mobInteract`、`usePlayerItem`、`spawnChildFromBreeding`、繁殖冷却与幼体流程。
- `world/entity/ai/control/FlyingMoveControl.java` 与 `ai/navigation/FlyingPathNavigation.java`：当前版本构造器和飞行控制。
- `world/entity/ai/goal/TemptGoal.java`：1.21.1 的 `Predicate<ItemStack>` 参数；没有误用旧版 Ingredient 构造。
- `world/entity/ai/goal/target/HurtByTargetGoal.java` 与 `TargetGoal.java`：受伤来源与目标记忆；据此处理片角羊反击超时后旧目标被重新设置的问题。
- `world/entity/projectile/ThrowableItemProjectile.java`、`Snowball.java`、`Projectile.java`：构造、物品模型、owner、真实碰撞与 `shoot`。
- `world/damagesource/DamageSources.java:209`：`mobProjectile(Entity, LivingEntity)`。
- `world/phys/AABB.java:39` 与 `:60`：`AABB(Vec3, Vec3)` 和 `encapsulatingFullBlocks(BlockPos, BlockPos)`；测试曾误用两 BlockPos 构造，已据源码修正。
- `world/entity/boss/wither/WitherBoss.java:372`：`ServerBossEvent` 玩家追踪/移除接口。
- `client/renderer/entity/LightningBoltRenderer.java`：`RenderType.lightning()` 与 `VertexConsumer.addVertex(...).setColor(...)`。
- `gametest/framework/GameTestHelper.java`：实际生成、存活计时、MockPlayer、相对坐标与断言接口。

加载器源码根：`E:/模组/mc源码/1.21.1-neoforge-21.1.233-sources/src`，核对 `RegisterSpawnPlacementsEvent`、`DeferredSpawnEggItem`、`EntityRenderersEvent`。追加祭台验收时核对 `common/util/FakePlayer.java` 与 `FakePlayerFactory.java`；对应原版源码核对 `ServerPlayerGameMode.useItemOn`、`changeGameModeForPlayer` 和 `MinecraftServer.setDifficulty`，确保假玩家绑定真实末地并按生存模式扣除物品。

GeckoLib 源码根：`E:/模组/1211/源码/geckolib-1.21.1/common/src/main/java/software/bernie/geckolib`，核对 `GeoEntity`、`AnimationController`、`RawAnimation`、`GeoEntityRenderer.renderFinal`、`GeoRenderer.reRender`、`GeoRenderLayer` 与 `AutoGlowingTexture`。

## 验证与边界

主任务执行 `compileJava + processResources`，`build/prismatic/compile-batch-b.log` 显示成功。第三次基础验证为 17 项通过；追加真实自然定位与祭台验收后，最终执行 `-PprismaticTests runGameTestServer`，`build/prismatic/gametest-sixth.log:512` 至 `:513` 在 2026-09-06 01:15:57 显示 **19 项必需测试全部通过**，`:529` 显示构建成功。本实体任务七项测试和五个物种最后补充的 `isNoAi()` 守卫均已纳入后续实际编译与回归验证。

本任务七项通过测试为：

1. 三种产物真实喂食、等待、一次采收、防重复、存档字段往返和重载后冷却。
2. 两只棱兔通过原版繁殖目标生成一个持久化幼体。
3. 首领锁线前摇无伤害，无掩体命中，锁线后新放墙仍能挡住。
4. 真正生成晶针并在掩体前碰撞移除。
5. 首领两次阶段转换、巢点/阶段存档、离巢返航标志与不回血。
6. 片角羊未受击保持中立、受击后前摇顶击、15 秒后平静。
7. 暮翅蛾在真实花丛上方保持飞行并移动。

天然庭院祭台也已通过真实末地交互验收。`build/prismatic/gametest-sixth.log:480` 和 `:482` 记录祭台坐标 `(2920,64,5432)`、首领巢点 `(2920,65,5436)`、首领 UUID `36265099-bc01-4928-b0e2-8ceb9678427d`，以及 `keys=3->2, peaceful=retained, obstruction=retained, repeated=retained`。辅助检查同时断言只产生一个持久化首领、重复使用保留同一 UUID、巢点属于实际庭院结构且 `PrismaticNest` NBT 正确保存。和平和遮挡用例均在无首领时先验证，避免误走重复占用分支；完成或异常后恢复临时难度、方块和假玩家状态，并清理此次测试首领。

第五轮唯一未通过项来自 GameTestServer 默认关闭自然结构，未执行到祭台，不能作为祭台成功证据。主任务修复仅测试环境的结构生成选项后，第六轮才取得上述完整证据。此前 AABB 测试编译错误与动态群系 bootstrap 错误也均已修复；本报告只把实际执行的测试结果列为通过。

本任务未启动客户端。仍待真实客户端验证：11 种实体实际模型、动画和局部发光；光束/扇线/环的玩家视角可读性；联机追踪新进入玩家的动作插值；首领完整返巢路径是否受庭院柱子阻挡；平衡、帧率与最终碰撞体手感。上述 GameTest 不证明这些视觉或联机观察项。

主任务需统一提供中英文的唯一新增消息键：`message.hp_end_expansion.prismatic_product_growing`（参数：剩余秒数）；主任务已确认接入。资源、实体战利品、自然群系刷怪表与结构一次性精英生成由对应主任务负责，不在本任务重复注册。
