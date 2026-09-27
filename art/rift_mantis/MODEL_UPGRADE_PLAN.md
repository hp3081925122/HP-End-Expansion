# 裂隙螳螂建模优化计划

## 现状

- 工程：`art/rift_mantis/rift_mantis.bbmodel`（GeckoLib Animated Model），44 体块、25 骨骼、128×128 贴图。
- 游戏资源：`src/main/resources/assets/hp_end_expansion/geo/rift_mantis.geo.json`、`textures/entity/rift_mantis.png`、`textures/entity/rift_mantis_glowmask.png`、`animations/rift_mantis.animation.json`。
- 参考：`reference/rift_mantis_reference.png`、`reference/palette.json`。
- 游戏内渲染放大 1.5 倍（`RiftMantisRenderer#withScale`），碰撞箱 2.7×3.6。

## 约束

- 25 个骨骼名称与父子层级、pivot 保持不变：8 个动画（idle、walk、slash、blink、blade、tear、hurt、death）和 Java 代码依赖它们。新增体块挂到现有骨骼下；确需新骨骼时只作为叶子骨骼新增。
- 改几何后必须重新检查 8 个动画的穿模和接地，尤其 death、tear、walk。
- 贴图按 `minecraft-creature-model-animation` 的像素贴图要求重画新增区域；扩图集时同步换算全部 UV，保持像素密度（约每模型单位 2 纹素）。
- 同步更新 `rift_mantis_glowmask.png`（尺寸必须与基础贴图一致），重新运行 `vfx/make_vfx_textures.py` 中的 `make_glow_layer` 或手工更新。
- 完成后导出 geo、贴图、动画，重新运行 `compileJava processResources`。

## 待优化部位

1. 步足：大腿、小腿、足端之间补关节节点，小腿收细，足端做成尖爪；每条腿约 3 块增至 6 块。
2. 镰臂：股节补前缘棱；镰刃内侧补 3～4 根短刺（细块或透明面片）；镰钩收尖。
3. 胸颈：拆成 2 节，补两侧前胸甲缘。
4. 头部：补一对上颚与一对下颚须，复眼改为略凸出的块。
5. 腹部：甲片后缘补搭接边，腹侧节线，尾端补分叉尾须。
6. 图集：视新增面积扩展到 128×256 或重新排布。

## 验收

- 每个部位在 BB 中与参考图局部并排比较，并查看贴图最近邻放大图。
- 整模连续环绕一圈并改变俯仰角检查闪烁与缺面。
- 8 个动画逐个完整播放，正面与侧面检查穿模、接地和循环接缝。
- 保留截图与帧记录到 `qa/`。
