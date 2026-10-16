package com.cooptest.fabricshim.fabric.api.entity.event.v1;
import com.cooptest.fabricshim.fabric.api.event.Event;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
public final class ServerLivingEntityEvents {
    public static final Event<AllowDamage> ALLOW_DAMAGE = new Event<>();
    public static final Event<AllowDeath> ALLOW_DEATH = new Event<>();
    public static final Event<AfterDeath> AFTER_DEATH = new Event<>();
    @FunctionalInterface public interface AllowDamage { boolean allowDamage(LivingEntity entity, DamageSource source, float amount); }
    @FunctionalInterface public interface AllowDeath { boolean allowDeath(LivingEntity entity, DamageSource damageSource, float damageAmount); }
    @FunctionalInterface public interface AfterDeath { void afterDeath(LivingEntity entity, DamageSource damageSource); }
}
