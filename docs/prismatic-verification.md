# 折光荒原验证与集成记录

## 当前证据

| 项目 | 结果 | 证据范围 |
|---|---|---|
| 工作隔离 | 已建立 | `feature/prismatic-wastes`，独立目录，基线 `5eaf1a3`；未改其他任务工作区 |
| 工具链 | 已确认 | Minecraft 1.21.1 / NeoForge 21.1.233 / GeckoLib 4.9.2 / Java 21.0.11 / Gradle 8.8 |
| 基础版本编译 | 通过 | 创建 worktree 后 compileJava + processResources |
| B 批主体编译 | 通过 | `build/prismatic/compile-batch-b.log`，2026-09-06，19 秒成功 |
| 资源交叉引用 | 通过 | `tools/prismatic/verify_content.py` 检查 521 处路径、27 方块、16 材料、11 生物，无错误；仅证明资源连接 |
| 离线视觉初查 | 已查看 | `art/prismatic/previews/entity-contact-sheet.png` 与 block-item-contact-sheet，独立轮廓/像素纹理可见；进一步动画和 UV 审查由资源报告记录 |
| 数据运行时加载 | 通过 | 第三轮 GameTest 正式启动并保存三个维度；runData 仅能证明模组初始化，不能单独证明动态注册表正确 |
| 植物/物品 GameTest | 4 项通过 | 第三轮涵盖六植物采收防重复、有效/无效施肥、真正树苗成长、错误维度祭台 |
| 实体/战斗 GameTest | 7 项通过，末次修正已复验 | 第六轮涵盖产物存档冷却、兔繁殖、羊反击、蛾绕花、晶针及 Boss 遮挡、阶段与巢点 |
| 群系与结构 GameTest | 8 项通过 | 第六轮涵盖 End codec 与群系分布、四布局实放及重载幂等、300 柱采样等价、原版自然定位、完整区块生成与合法祭台交互 |
| 正式构建与产物 | 通过 | 不带测试开关 clean build，18 秒；JAR 的 356 处资源与源码字节一致、36 个功能类齐全，无测试类/模板/配置，详见日志摘录 |
| 外部任务终态 | 尚未整体完成 | 2026-09-06 最新两目标均 idle；「悬潮庭」最终消息明确生物、精英、Boss、结构尚未完成，停止运行不等于达到合并条件 |
| Blockbench MCP 服务 | 在线 | health 返回 status=ok；当前会话未暴露专用工具，尚未进入项目或宣称做过模型检查 |
| 合并与 Git 推送 | 完整功能已推送，合并未开始 | `10f3c5e1edbd8e979a8d7127dc88491dc4ff7997` 已推送 origin/feature/prismatic-wastes，ls-remote 核验一致；等待两外部任务整体完成 |

## 实际失败、修复与性能记录

- `gametest-first.log`：测试代码误用当前版本不存在的 `AABB(BlockPos, BlockPos)` 构造，改为已核验的 `AABB.encapsulatingFullBlocks`。
- `gametest-second.log`：启动失败，`RegistrySetBuilder` 报自定义群系未绑定。根因是 vanilla bootstrap 阶段的 `HolderGetter.get` 会创建未绑定占位符，并非纯查询；删除 `TheEndBiomeSource.create` 注入，仅在真实世界解码时通过 `RegistryOps` 绑定。该失败的 Gradle 进程仍返回 0，故增加严格验证脚本要求日志出现实际测试完成标记。
- `gametest-third.log`：服务端实际 **17/17 required tests passed**，测试 7.971 秒，总构建 39 秒。此时最大庭院预检最慢约 1741 毫秒，触发 5958 毫秒服务端滞后，不能把通过功能测试当作性能合格。
- 优化依据：当前版本 `NoiseBasedChunkGenerator.iterateNoiseColumn` 每柱重建 `NoiseChunk`。新增局部批量采样器，每个噪声单元复用原版插值缓存，仍检查完整占地每柱及三格承重；不载入远区块、不保存跨世界缓存。新增 3 个种子、300 柱原版高度对照与承重对照，必须实际通过后才接受优化。
- `gametest-fourth.log`：新增采样器编译失败，原版 `BeardifierMarker` 是 protected。修为调用其公共接口 `BeardifierOrMarker.CODEC` 的原版 unit codec 取得同一单例，无访问转换器或反射。
- `gametest-fifth.log`：18/19 通过，采样等价通过；自然定位失败。源码证明 `GameTestServer.WORLD_OPTIONS` 固定关闭结构生成，原版 `ChunkStatusTasks` 因而跳过结构创建。增加仅在 `-PprismaticTests` 时注册的测试 Mixin 开启结构，保留种子和原版末地生成器；旧测试世界已在当前工作区 `run/world-before-natural-validation` 备份，新建测试世界复验，未改用户存档。
- `gametest-sixth.log`：新测试世界 **19/19 required tests passed**，7.764 秒，总构建 39 秒。300 柱三种子批量采样 48.8 ms、原版逐柱 305.6 ms；本轮庭院单次预检最慢 54.7 ms。四种设施均由原版结构定位找到并完成 FULL 区块生成；庭院整体定位、加载和检查约 1566 ms，与单次预检耗时是不同指标。
- 天然庭院祭台 `(2920,64,5432)` 通过真实末地生存模式 FakePlayer 右键：成功一次生成一个首领且钥匙 3→2，和平、遮挡及重复使用保留钥匙，巢点 `(2920,65,5436)` 和存档一致。
- 最后清理构建输出并执行正式 `clean build`，实际 JAR 为 `build/libs/hp_end_expansion-1.0.jar`，SHA-256 `a2d2c36318a9a57d3a03cdee3b979e5bf3bd9359228c6a51b5ce5e5532876ea6`。没有测试混入配置或测试文件泄漏。稳定证据见 [验收摘录](prismatic-validation-evidence.txt)；原日志在 `build/prismatic/`，另在 `run/prismatic-validation-logs/` 保留本地备份。

已查看全部 11 个实体的动作图册、Boss 多视角图和两张资源总览，没有发现缺失的主要部件。离线检查不等于游戏画面：透明排序、成熟发光层、攻击提示和实际碰撞感受仍需要客户端目视；当前未自动启动客户端，也未声称执行 Blockbench 检查。

## 服务端自动验证命令

```powershell
$env:JAVA_HOME='C:/Users/30819/.jdks/ms-21.0.11'
./gradlew.bat compileJava processResources --console=plain
& tools/prismatic/validate_runtime.ps1 -LogName gametest-final.log
./gradlew.bat build --console=plain
D:/Anaconda2/python.exe tools/prismatic/verify_content.py
```

`prismaticTests` 为显式开发选项，正式构建不应包含 tools 中的测试类和模板。所有测试失败必须记录实际根因和修复，不把框架未执行用例算成通过。

## 具体玩法验收项目

1. `/locate biome hp_end_expansion:prismatic_wastes` 找到外岛陆地群系；核心岛与其他末地群系依旧存在。
2. 六种地面植物长成、右键采收、重复点击、肥料有效和无效目标、自然树与树苗成长及采伐腐叶。
3. 四种生态生物喂食、产物准备、冷却、繁殖、保存重载；不得重进区块重置冷却或复制产物。
4. 四种怪物前摇、锁向、命中、收招与遮挡；两精英的不同技能与必掉印记。
5. 四种结构的真实定位、地基、入口、箱子与精英仅生成一次；跨区块顺序加载后不重复生成。
6. 无效祭台、和平难度、遮挡场地、已有首领时保留钥匙；合法庭院一次消耗一把钥匙，Boss 三阶段、巢点和血条有效。
7. 资源包内模型/纹理/UV/动画引用、死亡动画与碰撞比例；实际客户端观察尚无直接证据时不能写作已测试。
8. 两外部分支完成后，记录各提交与远程备份哈希，最终集成验证所有群系共存，Blockbench 检查两任务完整模型清单，修复后再推送。

## 最终合并记录

尚未开始。2026-09-06 查询时另两个任务均已停止运行，但「悬潮庭」交接明确还有生物、精英、Boss 和结构未完成，未满足用户指定的合并前提。没有修改它们的工作区、强行合并半成品或把停止运行认作完成；最终其他模型的 Blockbench MCP 审查随合并一起保留待办。

本功能代码与资源备份：`10f3c5e1edbd8e979a8d7127dc88491dc4ff7997`，远程分支 `origin/feature/prismatic-wastes`。推送后已通过 `git ls-remote --heads origin refs/heads/feature/prismatic-wastes` 确认远程哈希一致。设计初稿备份 `b6969d7` 同样保留于历史。
