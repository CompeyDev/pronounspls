package xyz.devcomp.pronounspls.mixin.accessor;

import java.util.List;

import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerListS2CPacket.class)
public interface PlayerListS2CPacketAccessor {
    @Accessor("entries")
    void setEntries(List<PlayerListS2CPacket.Entry> entries);
}
