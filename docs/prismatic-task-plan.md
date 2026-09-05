# 折光荒原总任务表

来源：[设计](prismatic-wastes-design.md)；执行：[分批任务](prismatic-parallel-tasks.md)。☐ 未开始，◐ 进行中，☑ 完成。复杂度 S/M/L，不估算时长。

| ID | 里程碑/任务 | 复杂度 | 依赖 | 验收 | 文件 |
|---|---|---|---|---|---|
| ☑ T0 | M0 独立分支、环境核验、设计与契约 | M | 无 | 独立 worktree、冻结 ID/接口 | docs/prismatic-* |
| ◐ T1 | M1 方块植被、材料与入口 | L | T0 | 27 方块、16 材料的交互和数据 | content/prismatic/block、PrismaticContent |
| ☐ T2 | M1 生态与战斗实体 | L | T0 | 11 实体、AI/存档/技能与渲染 | content/prismatic/entity、client、PrismaticEntities |
| ☐ T3 | M1 独立视觉资源 | L | T0 | 11 模型+动画+UV；方块/物品资源 | assets、art/prismatic、tools/prismatic/assets* |
| ☐ T4 | M2 世界生成与 4 结构 | L | T1,T2 | 注册表可加载、可定位、有落地地基 | content/prismatic/worldgen、worldgen 数据 |
| ☐ T5 | M2 战利品、配方、游戏内说明与整合 | M | T1,T2,T3 | 获取流程与中英文覆盖，无悬空资源 | data、lang、入口 |
| ☐ T6 | M3 实现验证与模型审查 | L | T4,T5 | 编译、加载、GameTest、资源与模型检查 | tools/prismatic、docs/prismatic-verification.md |
| ☐ T7 | M4 外部任务备份与最终集成 | L | T6、两任务完成 | 合并前 push；冲突与世界生成整合；验证后 push | 集成分支及记录 |
| ☐ T8 | M4 Blockbench MCP 检查两任务模型 | L | T7 | 所有模型检查、必要修复、再验证并 push | 模型与审查记录 |

关键路径：T0 → T1/T2/T3 → T4/T5 → T6 → T7 → T8。

待核验：群系 RegistryOps 接入、结构持久化、实体技能命中窗口、MCP 连接、最终多群系共存。风险与缓解参见设计第 9 节。

完成定义：设计第 10 节每项具有当前文件、测试输出或 MCP 图像证据；待测条目不能勾选。每批完成后修改此表对应行并记录输出，不重写其他任务行。
