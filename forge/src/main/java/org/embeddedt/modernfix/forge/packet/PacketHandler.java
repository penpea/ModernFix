package org.embeddedt.modernfix.forge.packet;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.embeddedt.modernfix.ModernFix;
import org.embeddedt.modernfix.ModernFixClient;
import org.embeddedt.modernfix.packet.EntityIDSyncPacket;

import java.util.function.Supplier;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModernFix.MODID, "main"),
            () -> PROTOCOL_VERSION,
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION),
            NetworkRegistry.acceptMissingOr(PROTOCOL_VERSION)
    );

    public static void register() {
        int id = 1;
        INSTANCE.registerMessage(id++, EntityIDSyncPacket.class, EntityIDSyncPacket::serialize, EntityIDSyncPacket::deserialize, PacketHandler::handleSyncPacket);
    }

    private static void handleSyncPacket(EntityIDSyncPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            contextSupplier.get().enqueueWork(() -> ModernFixClient.handleEntityIDSync(packet));
            contextSupplier.get().setPacketHandled(true);
        });
    }
}
