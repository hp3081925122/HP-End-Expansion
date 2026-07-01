# 虚空鲸 Blockbench 精修说明

## 导入资源

- 模型：`src/main/resources/assets/hp_end_expansion/geo/void_whale.geo.json`
- 动画：`src/main/resources/assets/hp_end_expansion/animations/void_whale.animation.json`
- 贴图：`src/main/resources/assets/hp_end_expansion/textures/entity/void_whale.png`

## 骨骼层级

- `root`
- `body`
- `head`
- `tail_base`
- `tail_tip`
- `tail_fin_left`
- `tail_fin_right`
- `left_fin`
- `right_fin`

## 精修重点

- `tail_base` 是尾巴根部摆动骨骼，pivot 在身体尾端。
- `tail_tip` 是空骨骼，用来承接尾梢二段摆动。
- `tail_fin_left` 和 `tail_fin_right` 是真正独立的分叉尾鳍骨骼，pivot 放在分叉根部。
- 游动动画优先调 `tail_base` 和 `tail_tip` 的相位差，再微调两个尾鳍的张合。
- 待机动画保持尾部低幅度摆动，避免静止时像硬模型。
- 传送和闪避动画只做短促压缩、尾部甩动和鳍收拢，不建议做太长。

## MCP 状态

本次尝试连接 Blockbench MCP 时，`http://localhost:3000/bb-mcp` 不可用，所以没有实际操作已打开的 Blockbench 工程。打开 Blockbench 和 MCP 后，可以继续按上面的骨骼名直接微调。
