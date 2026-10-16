package com.cooptest.client.anim;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/** Owns one controller per client-side player and applies its output to models and render matrices. */
public final class CoopAnimLib {
    private static final Map<AbstractClientPlayerEntity, PlayerAnimationController> CONTROLLERS = new WeakHashMap<>();

    private CoopAnimLib() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterClientReloadListenersEvent e) -> e.registerReloadListener(new AnimationRegistry()));
        MinecraftForge.EVENT_BUS.addListener(CoopAnimLib::onClientTick);
    }

    private static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) {
            CONTROLLERS.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            PlayerAnimationController c = CONTROLLERS.get(p);
            if (c != null) c.tick();
        }
    }

    public static PlayerAnimationController controller(AbstractClientPlayerEntity player) {
        return CONTROLLERS.computeIfAbsent(player, CoopAnimLib::create);
    }

    private static PlayerAnimationController create(AbstractClientPlayerEntity player) {
        PlayerAnimationFactory f = PlayerAnimationFactory.ANIMATION_DATA_FACTORY;
        if (f.factory != null) return f.factory.apply(player);
        return new PlayerAnimationController(player, (c, s, a) -> PlayState.STOP);
    }

    /** Existing, active controller for this entity, or null. */
    public static PlayerAnimationController active(Entity entity) {
        if (!(entity instanceof AbstractClientPlayerEntity p)) return null;
        PlayerAnimationController c = CONTROLLERS.get(p);
        return c != null && c.isActive() ? c : null;
    }

    /** True when the local camera player is being drawn with its own third-person model in first person. */
    public static boolean thirdPersonModelActive(Entity entity) {
        PlayerAnimationController c = active(entity);
        return c != null && c.getFirstPersonMode() == FirstPersonMode.THIRD_PERSON_MODEL;
    }

    public static void process(AbstractClientPlayerEntity player, float tickDelta) {
        PlayerAnimationController c = CONTROLLERS.get(player);
        if (c != null) c.process(tickDelta);
    }

    // ---- application helpers (PAL RenderUtil equivalents) ----

    private static final ThreadLocal<PlayerAnimationController.Bone> SCRATCH = ThreadLocal.withInitial(PlayerAnimationController.Bone::new);

    /** Copy vanilla pose into bone space, overlay animated axes, write back to both model layers. */
    public static void updatePart(PlayerAnimationController c, String name, ModelPart part, ModelPart secondLayer) {
        PlayerAnimationController.Bone bone = SCRATCH.get();
        ModelTransform init = part.getDefaultTransform();
        bone.posX = part.pivotX - init.pivotX;
        bone.posY = -(part.pivotY - init.pivotY);
        bone.posZ = part.pivotZ - init.pivotZ;
        bone.rotX = part.pitch;
        bone.rotY = part.yaw;
        bone.rotZ = part.roll;
        bone.scaleX = part.xScale;
        bone.scaleY = part.yScale;
        bone.scaleZ = part.zScale;
        c.apply(name, bone);
        writePart(part, bone, init);
        if (secondLayer != null) writePart(secondLayer, bone, init);
    }

    private static void writePart(ModelPart part, PlayerAnimationController.Bone bone, ModelTransform init) {
        part.pivotX = bone.posX + init.pivotX;
        part.pivotY = -bone.posY + init.pivotY;
        part.pivotZ = bone.posZ + init.pivotZ;
        part.pitch = bone.rotX;
        part.yaw = bone.rotY + init.yaw;
        part.roll = bone.rotZ;
        part.xScale = bone.scaleX;
        part.yScale = bone.scaleY;
        part.zScale = bone.scaleZ;
    }

    /** Whole-player "body" bone, applied after vanilla setupTransforms (PAL PlayerRendererMixin). */
    public static void applyBody(PlayerAnimationController c, MatrixStack matrices) {
        PlayerAnimationController.Bone body = SCRATCH.get();
        body.reset();
        if (!c.apply("body", body)) return;
        matrices.scale(body.scaleX, body.scaleY, body.scaleZ);
        matrices.translate(-body.posX / 16f, body.posY / 16f + 0.75f, body.posZ / 16f);
        float rx = -body.rotX;
        float ry = -body.rotY;
        MatrixStack.Entry entry = matrices.peek();
        entry.getPositionMatrix().rotateZYX(body.rotZ, ry, rx);
        entry.getNormalMatrix().rotateZYX(body.rotZ, ry, rx);
        matrices.translate(0, -0.75f, 0);
    }

    public static boolean isCameraPlayer(Entity entity) {
        return entity != null && entity == MinecraftClient.getInstance().getCameraEntity();
    }
}
