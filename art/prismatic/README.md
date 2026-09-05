# 折光荒原可编辑资源

正式运行资源在 `src/main/resources/assets/hp_end_expansion/` 下各 `prismatic/` 目录。这里保存原创资源的可编辑工程和离线审查证据。

- `models/`：11 个 `.bbmodel`，包含同一张正式 PNG、per-face UV、骨骼和六类动作；尚未在 Blockbench 中实际导入验证。
- `uv/`：每个实体全部面的独立图案分配清单。
- `previews/entity-contact-sheet.png`：11 种独立轮廓。
- `previews/block-item-contact-sheet.png`：16px 方块、植物年龄与材料实际 PNG。
- `previews/*-turnaround.png`：正面、斜前、斜后、特殊攻击蓄力。
- `previews/*-actions.png`：六类动作的关键时间点。
- `validation.json`：资源路径、UV、静态共面、骨骼与动作采样检查结果。

制作源为 `tools/prismatic/assets_entities.py`、`assets_blocks.py`、`assets_review.py`。按此顺序运行会重新生成资源和报告；先保留任何后续人工编辑，再运行制作脚本，以免覆盖人工编辑。生成过程不修改 Java、语言或 data。

所有图册是从正式 JSON 和 PNG 读取的离线软件渲染。它们不是 Blockbench 或游戏截图，不能替代客户端动画、透明排序与碰撞尺度验证。完整交接内容见 `docs/prismatic-assets-report.md`。
