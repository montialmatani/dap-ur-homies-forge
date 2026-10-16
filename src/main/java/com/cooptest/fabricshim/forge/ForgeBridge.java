package com.cooptest.fabricshim.forge;

import com.cooptest.fabricshim.fabric.api.command.v2.CommandRegistrationCallback;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import com.cooptest.fabricshim.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import com.cooptest.fabricshim.fabric.api.event.lifecycle.v1.ServerTickEvents;
import com.cooptest.fabricshim.fabric.api.event.player.AttackBlockCallback;
import com.cooptest.fabricshim.fabric.api.event.player.AttackEntityCallback;
import com.cooptest.fabricshim.fabric.api.event.player.PlayerBlockBreakEvents;
import com.cooptest.fabricshim.fabric.api.event.player.UseBlockCallback;
import com.cooptest.fabricshim.fabric.api.event.player.UseEntityCallback;
import com.cooptest.fabricshim.fabric.api.event.player.UseItemCallback;
import com.cooptest.fabricshim.fabric.api.itemgroup.v1.ItemGroupEvents;
import com.cooptest.fabricshim.fabric.api.networking.v1.ServerPlayConnectionEvents;
import com.cooptest.fabricshim.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceReloader;
import net.minecraft.resource.ResourceType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttackEntityEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Fires the Fabric-shaped shim events from Forge's common (both-sides) events. */
public final class ForgeBridge {
    private ForgeBridge() {}

    public static void register(IEventBus modBus) {
        IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener(ForgeBridge::onServerTick);
        bus.addListener(ForgeBridge::onServerStarting);
        bus.addListener(ForgeBridge::onServerStarted);
        bus.addListener(ForgeBridge::onServerStopping);
        bus.addListener(ForgeBridge::onServerStopped);
        bus.addListener(ForgeBridge::onRegisterCommands);
        bus.addListener(ForgeBridge::onLogin);
        bus.addListener(ForgeBridge::onLogout);
        bus.addListener(ForgeBridge::onChangeDimension);
        bus.addListener(ForgeBridge::onEntityInteractSpecific);
        bus.addListener(ForgeBridge::onEntityInteract);
        bus.addListener(ForgeBridge::onAttackEntity);
        bus.addListener(ForgeBridge::onRightClickBlock);
        bus.addListener(ForgeBridge::onRightClickItem);
        bus.addListener(ForgeBridge::onLeftClickBlock);
        bus.addListener(ForgeBridge::onBlockBreak);
        bus.addListener(ForgeBridge::onLivingAttack);
        bus.addListener(EventPriority.HIGH, ForgeBridge::onAllowDeath);
        bus.addListener(EventPriority.LOWEST, ForgeBridge::onAfterDeath);
        bus.addListener(ForgeBridge::onAddReloadListeners);
        modBus.addListener(ForgeBridge::onBuildTabs);
    }

    private static MinecraftServer server(TickEvent.ServerTickEvent e) {
        MinecraftServer s = e.getServer();
        return s != null ? s : ServerLifecycleHooks.getCurrentServer();
    }

    private static void onServerTick(TickEvent.ServerTickEvent e) {
        MinecraftServer s = server(e);
        if (e.phase == TickEvent.Phase.START) {
            for (ServerTickEvents.StartTick l : ServerTickEvents.START_SERVER_TICK.listeners()) l.onStartTick(s);
        } else {
            for (ServerTickEvents.EndTick l : ServerTickEvents.END_SERVER_TICK.listeners()) l.onEndTick(s);
        }
    }

    private static void onServerStarting(ServerStartingEvent e) {
        for (ServerLifecycleEvents.ServerStarting l : ServerLifecycleEvents.SERVER_STARTING.listeners()) l.onServerStarting(e.getServer());
    }

    private static void onServerStarted(ServerStartedEvent e) {
        for (ServerLifecycleEvents.ServerStarted l : ServerLifecycleEvents.SERVER_STARTED.listeners()) l.onServerStarted(e.getServer());
    }

    private static void onServerStopping(ServerStoppingEvent e) {
        for (ServerLifecycleEvents.ServerStopping l : ServerLifecycleEvents.SERVER_STOPPING.listeners()) l.onServerStopping(e.getServer());
    }

    private static void onServerStopped(ServerStoppedEvent e) {
        for (ServerLifecycleEvents.ServerStopped l : ServerLifecycleEvents.SERVER_STOPPED.listeners()) l.onServerStopped(e.getServer());
    }

    private static void onRegisterCommands(RegisterCommandsEvent e) {
        for (CommandRegistrationCallback l : CommandRegistrationCallback.EVENT.listeners()) {
            l.register(e.getDispatcher(), e.getBuildContext(), e.getCommandSelection());
        }
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayerEntity sp) {
            for (ServerPlayConnectionEvents.Join l : ServerPlayConnectionEvents.JOIN.listeners()) {
                l.onPlayReady(sp.networkHandler, p -> CoopNetwork.sendToPlayer(sp, p), sp.getServer());
            }
        }
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayerEntity sp) {
            for (ServerPlayConnectionEvents.Disconnect l : ServerPlayConnectionEvents.DISCONNECT.listeners()) {
                l.onPlayDisconnect(sp.networkHandler, sp.getServer());
            }
        }
    }

    private static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayerEntity sp) {
            MinecraftServer s = sp.getServer();
            ServerWorld from = s.getWorld(e.getFrom());
            ServerWorld to = s.getWorld(e.getTo());
            for (ServerEntityWorldChangeEvents.AfterPlayerChange l : ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.listeners()) {
                l.afterChangeWorld(sp, from, to);
            }
        }
    }

    private static ActionResult useEntity(PlayerEntity player, World world, Hand hand, net.minecraft.entity.Entity target, EntityHitResult hit) {
        for (UseEntityCallback l : UseEntityCallback.EVENT.listeners()) {
            ActionResult r = l.interact(player, world, hand, target, hit);
            if (r != ActionResult.PASS) return r;
        }
        return ActionResult.PASS;
    }

    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) {
        EntityHitResult hit = new EntityHitResult(e.getTarget(), e.getLocalPos().add(e.getTarget().getPos()));
        ActionResult r = useEntity(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget(), hit);
        if (r != ActionResult.PASS) {
            e.setCancellationResult(r);
            e.setCanceled(true);
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        ActionResult r = useEntity(e.getEntity(), e.getLevel(), e.getHand(), e.getTarget(), null);
        if (r != ActionResult.PASS) {
            e.setCancellationResult(r);
            e.setCanceled(true);
        }
    }

    private static void onAttackEntity(AttackEntityEvent e) {
        PlayerEntity p = e.getEntity();
        for (AttackEntityCallback l : AttackEntityCallback.EVENT.listeners()) {
            ActionResult r = l.interact(p, p.getWorld(), Hand.MAIN_HAND, e.getTarget(), null);
            if (r != ActionResult.PASS) {
                e.setCanceled(true);
                return;
            }
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        for (UseBlockCallback l : UseBlockCallback.EVENT.listeners()) {
            ActionResult r = l.interact(e.getEntity(), e.getLevel(), e.getHand(), e.getHitVec());
            if (r != ActionResult.PASS) {
                e.setCancellationResult(r);
                e.setCanceled(true);
                return;
            }
        }
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        for (UseItemCallback l : UseItemCallback.EVENT.listeners()) {
            TypedActionResult<ItemStack> r = l.interact(e.getEntity(), e.getLevel(), e.getHand());
            if (r != null && r.getResult() != ActionResult.PASS) {
                e.setCancellationResult(r.getResult());
                e.setCanceled(true);
                return;
            }
        }
    }

    private static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        for (AttackBlockCallback l : AttackBlockCallback.EVENT.listeners()) {
            ActionResult r = l.interact(e.getEntity(), e.getLevel(), e.getHand(), e.getPos(), e.getFace());
            if (r != ActionResult.PASS) {
                e.setCanceled(true);
                return;
            }
        }
    }

    private static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof World world)) return;
        for (PlayerBlockBreakEvents.Before l : PlayerBlockBreakEvents.BEFORE.listeners()) {
            if (!l.beforeBlockBreak(world, e.getPlayer(), e.getPos(), e.getState(), world.getBlockEntity(e.getPos()))) {
                e.setCanceled(true);
                return;
            }
        }
    }

    private static void onLivingAttack(LivingAttackEvent e) {
        LivingEntity entity = e.getEntity();
        if (entity.getWorld().isClient) return;
        for (ServerLivingEntityEvents.AllowDamage l : ServerLivingEntityEvents.ALLOW_DAMAGE.listeners()) {
            if (!l.allowDamage(entity, e.getSource(), e.getAmount())) {
                e.setCanceled(true);
                return;
            }
        }
    }

    private static void onAllowDeath(LivingDeathEvent e) {
        LivingEntity entity = e.getEntity();
        if (entity.getWorld().isClient) return;
        for (ServerLivingEntityEvents.AllowDeath l : ServerLivingEntityEvents.ALLOW_DEATH.listeners()) {
            if (!l.allowDeath(entity, e.getSource(), entity.getHealth())) {
                e.setCanceled(true);
                if (entity.getHealth() <= 0.0F) entity.setHealth(1.0F);
                return;
            }
        }
    }

    private static void onAfterDeath(LivingDeathEvent e) {
        if (e.isCanceled()) return;
        LivingEntity entity = e.getEntity();
        if (entity.getWorld().isClient) return;
        for (ServerLivingEntityEvents.AfterDeath l : ServerLivingEntityEvents.AFTER_DEATH.listeners()) {
            l.afterDeath(entity, e.getSource());
        }
    }

    private static void onAddReloadListeners(AddReloadListenerEvent e) {
        for (ResourceReloader r : ResourceManagerHelper.get(ResourceType.SERVER_DATA).listeners) e.addListener(r);
    }

    private static void onBuildTabs(BuildCreativeModeTabContentsEvent e) {
        var event = ItemGroupEvents.EVENTS.get(e.getTabKey());
        if (event == null) return;
        for (ItemGroupEvents.ModifyEntries l : event.listeners()) l.modifyEntries(stack -> e.accept(stack));
    }
}
