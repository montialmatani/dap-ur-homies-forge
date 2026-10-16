package com.cooptest.fabricshim.fabric.api.networking.v1;
import com.cooptest.fabricshim.mc.CustomPayload;
@FunctionalInterface
public interface PacketSender { void sendPacket(CustomPayload payload); }
