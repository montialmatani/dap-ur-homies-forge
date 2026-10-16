package com.cooptest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import com.cooptest.fabricshim.loader.api.FabricLoader;

public class CoopMovesConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("coopmoves.json").toFile();
    private static CoopMovesConfig INSTANCE;
    public static final int CURRENT_CONFIG_VERSION = 4;
    public int configVersion = 0;
    public boolean noGriefMode = false;
    public boolean easyFusionTest = false;
    public boolean impactFramesEveryDap = false;
    public boolean enableGrab = true;
    public boolean enableSpin = true;
    public boolean enableGroundPound = true;
    public boolean enableDap = true;
    public boolean enableDapCombo = false;
    public boolean enableDapFusion = false;
    public boolean enableDapHold = true;
    public boolean enableFallDap = true;
    public boolean enableFireDap = true;
    public boolean enableHeavenDap = true;
    public long dapChargeWindowMs = 250L;
    public long dapReleaseWindowMs = 500L;
    public long dapPerfectWindowMs = 85L;
    public long dapCooldownMs = 1500L;
    public long dapWhiffCooldownMs = 800L;
    public long dapFireDelayMs = 2000L;
    public long dapFireBuildTimeMs = 2000L;
    public boolean heavenCraterEnabled = true;
    public int heavenCraterRadius = 150;
    public int heavenCraterDepth = 50;
    public int heavenCraterClearAbove = 90;
    public boolean heavenStripLeaves = true;
    public int heavenLeafRadius = 220;
    public boolean heavenPushEnabled = true;
    public int heavenPushRadius = 220;
    public double heavenPushMax = 4.5;
    public double heavenPushMin = 1.5;
    public double heavenPushUp = 1.1;
    public boolean heavenProtectPushedPlayers = true;
    public boolean heavenKillMobs = false;
    public int heavenColumnsPerTick = 1200;
    public int heavenMaxBlocksPerTick = 22000;
    public boolean dapBlastEnabled = true;
    public double dapBlastRadius = 3.5;
    public float dapBlastDamage = 6.0F;
    public double dapBlastKnockback = 2.0;
    public boolean perfectDapShockwaveEnabled = true;
    public double perfectDapShockwaveRadius = 10.0;
    public double perfectDapShockwaveStrength = 2.0;
    public boolean fireDapPlaceFire = true;
    public boolean fireDapBurnEntities = true;
    public int fireDapBurnSeconds = 5;
    public boolean fireDapKnockbackEnabled = true;
    public double fireDapKnockbackRadius = 50.0;
    public double fireDapKnockback = 15.0;
    public boolean fireComboBurnEntities = true;
    public int fireComboBurnSeconds = 5;
    public boolean fireComboKnockbackEnabled = true;
    public double fireComboRadius = 30.0;
    public double fireComboKnockback = 3.0;
    public float fireComboDamage = 20.0F;
    public boolean enableHighFive = true;
    public boolean enableHighFiveHug = true;
    public boolean enableHighFiveCombo = true;
    public boolean enableHighFivePass = false;
    public double highFiveReachForward = 0.7;
    public double highFiveReachDepth = 0.6;
    public double highFiveReachRightOffset = 0.0;
    public double highFiveReachWidth = 1.15;
    public float dapRunHearts = 1.0F;
    public float dapRunShake = 0.9F;
    public int dapRunShakeMs = 260;
    public int dapRunFlairBurstSec = 3;
    public int dapRunFlairBurstAmp = 4;
    public int dapRunFlairSprintSpeedSec = 30;
    public int dapRunFlairPerfectSpeedSec = 3;
    public boolean enableHighfiveDap = false;
    public boolean enablePush = true;
    public long pushArmHoldMs = 1500L;
    public double pushRange = 2.5;
    public double pushPowerLow = 0.5;
    public double pushPowerMedium = 1.8;
    public double pushPowerHigh = 3.5;
    public double pushCarriedMultiplier = 0.85;
    public boolean enableCatch = true;
    public boolean enableMarioJump = true;
    public boolean enableKick = true;
    public boolean enableDropKick = true;
    public boolean enableSlap = true;
    public boolean enableStrongSlapSmoothTp = true;
    public boolean enableNormalSlapSmoothTp = true;
    public boolean enableClap = true;
    public boolean enablePact = true;
    public long pactSealHoldMs = 2000L;
    public long pactHoldTimeoutMs = 400L;
    public long pactDurationMs = 1800000L;
    public float pactBetrayalDamage = 4.0F;
    public boolean pactBroadcastBetrayal = true;
    public int pactBarSegments = 20;
    public boolean enableRallyBeacon = true;
    public long rallyBeaconMs = 120000L;
    public boolean enableFlairBarrier = true;
    public long flairBarrierCleanMs = 5000L;
    public long flairBarrierPerfectMs = 8000L;
    public long flairBarrierOtherMs = 5000L;
    public boolean enableFlairBuffs = true;
    public boolean enableFlairBlast = true;
    public boolean enableHighFiveBuffs = true;
    public int hypeMaxStacks = 10;
    public long hypeDecayMs = 8000L;
    public int hypeMaxSpeedAmp = 9;
    public boolean enableHypeTornado = true;
    public int hypeTornadoStacks = 5;
    public double hypeTornadoRadius = 3.0;
    public double hypeTornadoRadiusPerStack = 0.35;
    public double hypeTornadoPush = 0.95;
    public int hypeTornadoParticles = 4;
    public boolean enableSpearStrike = true;
    public double spearAimRange = 6.0;
    public double spearAimConeDeg = 40.0;
    public double spearHomingTurn = 0.35;
    public double spearHomingMinSpeed = 0.9;
    public double spearHitRadius = 1.8;
    public float spearBaseDamage = 8.0F;
    public float spearSpeedDamage = 10.0F;
    public float spearMaxDamage = 24.0F;
    public double spearKnockback = 1.5;
    public double spearBounce = 0.45;
    public boolean enableSpearNuke = true;
    public float spearNukePower = 14.0F;
    public double spearNukeRadius = 14.0;
    public float spearNukeDamage = 60.0F;
    public boolean spearNukeKillsStriker = true;
    public boolean spearMissileMode = false;
    public double spearMissileRange = 160.0;
    public double spearMissileConeDeg = 20.0;
    public double spearMissileSpeed = 2.2;
    public double spearMissileTurn = 0.5;
    public double spearMissileHitRadius = 1.5;
    public float spearMissileDamage = 60.0F;
    public boolean enableDapHearts = true;
    public float dapHeartsClean = 0.0F;
    public float dapHeartsPerfect = 2.0F;
    public float dapHeartsGreat = 0.0F;
    public float dapHeartsFlairClean = 2.0F;
    public float dapHeartsFlairBig = 4.0F;
    public float dapHeartsMax = 8.0F;
    public float dapCleanHeal = 0.0F;
    public int dapHeartsSeconds = 60;
    public double brosWalkSpeed = 0.17;
    public double brosSprintMult = 1.35;
    public double brosStepHeight = 1.05;
    public double brosMaxYSplit = 1.15;
    public float brosShieldHp = 40.0F;
    public double brosDomeRadius = 2.2;
    public float brosMobDamageThrough = 0.65F;
    public float brosOtherDamageReduction = 0.4F;
    public float brosPartnerShare = 0.5F;
    public float brosHealAmount = 1.0F;
    public int brosHealIntervalTicks = 15;
    public int brosHealCombatPauseTicks = 80;
    public float brosShatterDamage = 18.0F;
    public int brosShieldRegenSeconds = 45;
    public float brosRushDamage = 18.0F;
    public int brosRushTicks = 8;
    public double brosRushSpeed = 0.75;
    public int brosRushCooldownTicks = 120;
    public float brosRushShieldCost = 6.0F;
    public boolean brosRushBreaksBlocks = true;
    public float brosRushMaxHardness = 3.0F;
    public double brosPulseRadius = 10.0;
    public float brosPulseDamage = 10.0F;
    public double brosPulseKnockback = 1.6;
    public float brosPulseShieldCost = 8.0F;
    public int brosPulseCooldownTicks = 160;
    public float brosBunkerDrain = 0.06F;
    public float brosBunkerDamageThrough = 0.08F;
    public double brosBunkerShoveRadius = 3.5;
    public double brosBunkerShovePower = 0.55;
    public int brosBunkerShoveInterval = 10;
    public int brosRechargeFullTicks = 80;
    public float brosRechargeHealthCost = 0.2F;
    public float brosRechargeMinHealth = 6.0F;

    public static CoopMovesConfig get() {
        if (INSTANCE == null) {
            load();
        }

        return INSTANCE;
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                CoopMovesConfig loaded = (CoopMovesConfig)GSON.fromJson(reader, CoopMovesConfig.class);
                INSTANCE = loaded != null ? loaded : new CoopMovesConfig();
                INSTANCE.migrate();
                save();
            } catch (Exception var6) {
                System.err.println("[CoopMoves] Config corrupted, resetting: " + var6.getMessage());

                try {
                    Files.copy(CONFIG_FILE.toPath(), CONFIG_FILE.toPath().resolveSibling("coopmoves.json.broken"), StandardCopyOption.REPLACE_EXISTING);
                    System.err.println("[CoopMoves] Old config saved as coopmoves.json.broken");
                } catch (Exception var3) {
                }

                INSTANCE = new CoopMovesConfig();
                save();
            }
        } else {
            INSTANCE = new CoopMovesConfig();
            save();
        }
    }

    private void migrate() {
        if (this.configVersion < 4) {
            if (this.configVersion < 2) {
                this.dapHeartsClean = 0.0F;
                this.dapHeartsGreat = 0.0F;
                this.dapHeartsPerfect = 2.0F;
                this.dapHeartsMax = 8.0F;
                this.dapCleanHeal = 0.0F;
                System.out.println("[CoopMoves] Config migrated to v2 (golden-heart rebalance).");
            }

            if (this.configVersion < 3) {
                this.brosBunkerDrain = 0.12F;
                this.brosBunkerShoveRadius = 3.0;
                this.brosBunkerShovePower = 0.28;
                this.brosBunkerShoveInterval = 10;
            }

            if (this.configVersion < 4) {
                this.brosBunkerDrain = 0.06F;
                this.brosBunkerShoveRadius = 3.5;
                this.brosBunkerShovePower = 0.55;
                this.brosRechargeHealthCost = 0.2F;
            }

            this.configVersion = 4;
        }
    }

    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();

            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (IOException var5) {
            System.err.println("[CoopMoves] Failed to save config: " + var5.getMessage());
        }
    }

    public static void reload() {
        INSTANCE = null;
        load();
    }
}
