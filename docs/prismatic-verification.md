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
| 数据运行时加载 | 进行中 | runData 日志 `build/prismatic/data-load.log` |
| 植物/物品 GameTest | 已编写，待运行 | `tools/prismatic/gametest/.../PrismaticPlantTests.java` |
| 实体/战斗 GameTest | 进行中 | 实体任务追加，尚未取得运行结果 |
| 群系与结构 GameTest | 进行中 | 世界生成任务追加，尚未取得运行结果 |
| 外部任务终态 | 尚未结束 | 2026-09-06 最近一次 wait_threads 两目标仍 active/inProgress |
| Blockbench MCP 服务 | 在线 | health 返回 status=ok；当前会话未暴露专用工具，尚未进入项目或宣称做过模型检查 |
| 合并与 Git 推送 | 未开始 | 等待本功能验证及两外部任务结束 |

## 服务端自动验证命令

```powershell
$env:JAVA_HOME='C:/Users/30819/.jdks/ms-21.0.11'
./gradlew.bat compileJava processResources --console=plain
./gradlew.bat -PprismaticTests runGameTestServer --console=plain
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

尚未开始。不得在这里预填成功结果。
