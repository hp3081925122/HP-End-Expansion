package org.hp.hp_end_expansion.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.hp.hp_end_expansion.Hp_end_expansion;
import org.hp.hp_end_expansion.entity.RiftMantisEntity;
import org.hp.hp_end_expansion.entity.RiftMatriarchEntity;
import org.hp.hp_end_expansion.entity.StarCoreEntity;
import org.hp.hp_end_expansion.entity.StarDevourerEntity;
import org.hp.hp_end_expansion.entity.VoidRayEntity;
import org.hp.hp_end_expansion.entity.starwreck.SkyrenderEntity;
import org.hp.hp_end_expansion.entity.starwreck.StarChaserEntity;

public final class CombatConfigs {
    public static final Settings RIFT_MANTIS = new Settings("rift_mantis", "裂隙螳螂", 160, 12, 10, 0.3, 40, 0.8, 0)
        .damage("slashDamage", 12, "双镰斩的基础伤害")
        .damage("blinkDamage", 14.4, "裂隙闪现后的斩击基础伤害")
        .damage("bladeDamage", 7, "裂隙飞刃每发伤害")
        .damage("tearDamage", 9, "裂隙撕裂地刺伤害")
        .finish();
    public static final Settings RIFT_MATRIARCH = new Settings("rift_matriarch", "裂隙螳后", 600, 18, 16, 0.32, 48, 1, 0)
        .damage("tripleFirstDamage", 14, "三连镰斩第一段基础伤害")
        .damage("tripleSecondDamage", 14, "三连镰斩第二段基础伤害")
        .damage("tripleFinisherDamage", 22, "三连镰斩第三段下劈基础伤害")
        .damage("huntDamage", 16, "裂隙追猎每次落点斩击伤害")
        .damage("stormBladeDamage", 10, "镰刃风暴每发飞刃伤害")
        .damage("webFissureDamage", 12, "裂隙织网每道地裂伤害")
        .damage("phaseThreeFissureDamage", 8, "第三阶段场地边缘地裂伤害")
        .damage("fieldDamage", 4, "裂隙场每秒伤害")
        .damage("finaleLineDamage", 20, "终结技每条斩线伤害")
        .damage("finaleImpactDamage", 25, "终结技落地范围伤害")
        .health("summonMinionHealth", 60, "召唤的裂隙螳螂生命上限")
        .finish();
    public static final Settings VOID_RAY = new Settings("void_ray", "裂空鳐", 130, 10, 6, 0.5, 48, 0.6, 0.5)
        .damage("diveDamage", 15, "俯冲直接命中伤害")
        .damage("beamDamage", 5, "虚空射线每次结算伤害，每十游戏刻结算一次")
        .damage("beamBlockedDamage", 2.5, "虚空射线命中举盾目标时每次伤害")
        .damage("vortexDamage", 3, "引力漩涡中心每次伤害，每十游戏刻结算一次")
        .damage("starfallDamage", 8, "星陨每颗落点伤害")
        .finish();
    public static final Settings STAR_DEVOURER = new Settings("star_devourer", "噬星鳐王", 520, 16, 10, 0.55, 64, 1, 0.55)
        .damage("diveDamage", 18, "螺旋俯冲路径直接命中伤害")
        .damage("diveImpactDamage", 10, "螺旋俯冲落地范围伤害")
        .damage("prismMainDamage", 7, "棱镜主射线每次伤害，每十游戏刻结算一次")
        .damage("prismMainBlockedDamage", 3.5, "棱镜主射线命中举盾目标时每次伤害")
        .damage("prismSubDamage", 4, "棱镜分裂射线每次伤害，每十游戏刻结算一次")
        .damage("prismSubBlockedDamage", 2, "棱镜分裂射线命中举盾目标时每次伤害")
        .damage("holePulseDamage", 5, "黑洞中心每次伤害，每十游戏刻结算一次")
        .damage("holeCollapseDamage", 12, "黑洞结束时的坍缩伤害")
        .damage("rainDamage", 10, "星陨暴雨每颗大陨石落地伤害")
        .damage("rainStardustDamage", 2, "星陨暴雨留下的星尘地面每秒伤害")
        .damage("phaseTwoStarDamage", 8, "第二阶段额外小星陨的落地伤害")
        .damage("spinDamage", 14, "星环旋斩每次命中伤害")
        .damage("devourDamage", 40, "吞星星爆完整伤害，按剩余星核比例衰减")
        .health("summonMinionHealth", 40, "召唤的裂空鳐护卫生命上限")
        .health("starCoreHealth", 30, "吞星生成的每颗星核生命上限")
        .finish();
    public static final Settings STAR_CHASER = new Settings("star_chaser", "逐星兽", 180, 8, 6, 0.32, 32, 0.6, 0)
        .health("maxHealthEasy", 140, "简单难度生命上限；普通难度使用 maxHealth")
        .health("maxHealthHard", 220, "困难难度生命上限")
        .damage("biteDamage", 8, "咬合伤害")
        .damage("chargeDamage", 10, "彗星冲锋伤害")
        .damage("sweepDamage", 5, "甩尾伤害")
        .damage("stompDamage", 11, "践踏爪下直接伤害")
        .damage("waveDamage", 6, "践踏震荡波伤害")
        .damage("summonStarDamage", 6, "召星技能每颗小陨星伤害，仅传给逐星兽自己的召星")
        .finish();
    public static final Settings SKYRENDER = new Settings("skyrender", "裂天之主", 800, 15, 14, 0.3, 48, 1, 0)
        .health("maxHealthEasy", 640, "简单难度生命上限；普通难度使用 maxHealth")
        .health("maxHealthHard", 1000, "困难难度生命上限")
        .damage("goreDamage", 15, "角挑伤害，第三阶段连挑每下都是这个值")
        .damage("rakeDamage", 12, "裂爪横扫伤害")
        .damage("tearDamage", 6, "爪扫后空中裂痕合拢的伤害")
        .damage("tailDamage", 14, "尾锤伤害")
        .damage("leapDamage", 18, "跃袭落地伤害")
        .damage("shardDamage", 8, "天幕坠片插地伤害，第三阶段天上零星坠片同值")
        .damage("gazeDamage", 24, "注视伤害")
        .damage("rendDamage", 10, "裂天每片碎片伤害")
        .damage("landDamage", 10, "召唤时坠落落地伤害")
        .damage("exhaustedMultiplier", 1.3, "注视后喘息时身体受伤倍率")
        .damage("eyeMultiplier", 1.6, "注视后喘息时打头的受伤倍率")
        .damage("explosionMultiplier", 0.25, "爆炸伤害额外倍率")
        .damage("maxHitFraction", 0.05, "单次受伤上限，占最大生命的比例")
        .damage("meteorTrigger", 0.5, "天陨的触发血线，占最大生命的比例；第二阶段只放一次")
        .damage("meteorHealthRatio", 0.5, "天陨落地后场内玩家的生命变为当前的多少，直接改血、不致死")
        .finish();
    private static final List<Settings> ALL = List.of(RIFT_MANTIS, RIFT_MATRIARCH, VOID_RAY, STAR_DEVOURER, STAR_CHASER, SKYRENDER);

    private CombatConfigs() {}

    public static void register(IEventBus bus, ModContainer container) {
        for (Settings settings : ALL) {
            container.registerConfig(ModConfig.Type.COMMON, settings.spec, settings.fileName());
        }
        bus.addListener(CombatConfigs::reload);
        NeoForge.EVENT_BUS.addListener(CombatConfigs::join);
    }

    private static void join(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob mob) apply(mob);
    }

    private static void reload(ModConfigEvent.Reloading event) {
        if (ALL.stream().noneMatch(settings -> settings.spec == event.getConfig().getSpec())) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        server.execute(() -> {
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    if (entity instanceof Mob mob) apply(mob);
                }
            }
        });
    }

    private static void apply(Mob mob) {
        Settings settings;
        double health;
        if (mob instanceof RiftMantisEntity mantis) {
            settings = RIFT_MANTIS;
            health = mantis.isMinion() ? RIFT_MATRIARCH.value("summonMinionHealth") : settings.value("maxHealth");
        } else if (mob instanceof RiftMatriarchEntity) {
            settings = RIFT_MATRIARCH;
            health = settings.value("maxHealth");
        } else if (mob instanceof VoidRayEntity ray) {
            settings = VOID_RAY;
            health = ray.isMinion() ? STAR_DEVOURER.value("summonMinionHealth") : settings.value("maxHealth");
        } else if (mob instanceof StarDevourerEntity) {
            settings = STAR_DEVOURER;
            health = settings.value("maxHealth");
        } else if (mob instanceof StarChaserEntity) {
            settings = STAR_CHASER;
            Difficulty difficulty = mob.level().getDifficulty();
            health = settings.value(difficulty == Difficulty.EASY ? "maxHealthEasy" : difficulty == Difficulty.HARD ? "maxHealthHard" : "maxHealth");
        } else if (mob instanceof SkyrenderEntity) {
            // 裂天之主按难度取三档生命
            settings = SKYRENDER;
            Difficulty difficulty = mob.level().getDifficulty();
            String key = "maxHealth";
            if (difficulty == Difficulty.EASY) key = "maxHealthEasy";
            else if (difficulty == Difficulty.HARD) key = "maxHealthHard";
            health = settings.value(key);
        } else if (mob instanceof StarCoreEntity) {
            float ratio = mob.getHealth() / mob.getMaxHealth();
            set(mob, Attributes.MAX_HEALTH, STAR_DEVOURER.value("starCoreHealth"));
            mob.setHealth(mob.getMaxHealth() * ratio);
            return;
        } else {
            return;
        }
        float ratio = mob.getHealth() / mob.getMaxHealth();
        set(mob, Attributes.MAX_HEALTH, health);
        set(mob, Attributes.ATTACK_DAMAGE, settings.value("attackDamage"));
        set(mob, Attributes.ARMOR, settings.value("armor"));
        set(mob, Attributes.ARMOR_TOUGHNESS, settings.value("armorToughness"));
        set(mob, Attributes.MOVEMENT_SPEED, settings.value("movementSpeed"));
        set(mob, Attributes.FOLLOW_RANGE, settings.value("followRange"));
        set(mob, Attributes.KNOCKBACK_RESISTANCE, settings.value("knockbackResistance"));
        if (settings.values.containsKey("flyingSpeed")) set(mob, Attributes.FLYING_SPEED, settings.value("flyingSpeed"));
        mob.setHealth(mob.getMaxHealth() * ratio);
    }

    private static void set(Mob mob, Holder<Attribute> attribute, double value) {
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    public static final class Settings {
        private final String id;
        private final ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        private final Map<String, ModConfigSpec.DoubleValue> values = new LinkedHashMap<>();
        private ModConfigSpec spec;

        private Settings(String id, String name, double health, double attack, double armor, double speed, double range, double resistance, double flyingSpeed) {
            this.id = id;
            builder.comment(name + "专用战斗配置。生命与伤害单位为点，两点等于一颗心。技能伤害不会修改共用辅助实体的默认值。")
                .push("attributes");
            define("maxHealth", health, 1, 1024, "基础生命上限；Boss 原有多人属性加成仍保留");
            define("attackDamage", attack, 0, 2048, "普通攻击属性；技能伤害由 skills 下各项独立设置");
            define("armor", armor, 0, 30, "基础护甲点数");
            define("armorToughness", 0, 0, 20, "基础护甲韧性");
            define("movementSpeed", speed, 0, 1024, "基础移动速度，保留阶段速度修饰符");
            define("followRange", range, 0, 2048, "追踪范围，单位为格");
            define("knockbackResistance", resistance, 0, 1, "击退抗性，零为无抗性，一为完全抵抗");
            if (flyingSpeed > 0) define("flyingSpeed", flyingSpeed, 0, 1024, "基础飞行速度");
            builder.pop();
        }

        private void define(String key, double defaultValue, double min, double max, String description) {
            values.put(key, builder.comment(description).defineInRange(key, defaultValue, min, max));
        }

        private Settings damage(String key, double defaultValue, String description) {
            builder.push("skills");
            define(key, defaultValue, 0, 65536, description);
            builder.pop();
            return this;
        }

        private Settings health(String key, double defaultValue, String description) {
            builder.push("attributes");
            define(key, defaultValue, 1, 1024, description);
            builder.pop();
            return this;
        }

        private Settings finish() {
            spec = builder.build();
            return this;
        }

        public String fileName() {
            return Hp_end_expansion.MODID + "-" + id + ".toml";
        }

        public double value(String key) {
            ModConfigSpec.DoubleValue value = values.get(key);
            if (value == null) throw new IllegalArgumentException("Unknown combat setting: " + id + "." + key);
            return spec.isLoaded() ? value.get() : value.getDefault();
        }

        public float damage(String key) {
            return (float) value(key);
        }
    }
}
