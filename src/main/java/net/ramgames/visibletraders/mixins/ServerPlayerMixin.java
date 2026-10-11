package net.ramgames.visibletraders.mixins;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.network.protocol.game.ClientboundMerchantOffersPacket;
import net.ramgames.visibletraders.MerchantMenuDuck;
import net.ramgames.visibletraders.VillagerDuck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Applies Visible Traders data to both initial and refreshed merchant packets. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @ModifyArg(
            method = "sendMerchantOffers",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundMerchantOffersPacket;<init>(ILnet/minecraft/world/item/trading/MerchantOffers;IIZZ)V"),
            index = 1
    )
    private MerchantOffers visibleTraders$appendLockedOffers(MerchantOffers offers) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!(player.containerMenu instanceof MerchantMenuDuck menuDuck)) return offers;
        if (!(menuDuck.visibleTraders$getTrader() instanceof Villager villager)) return offers;

        MerchantOffers complete = new MerchantOffers();
        complete.addAll(offers);
        complete.addAll(VillagerDuck.of(villager).visibleTraders$getLockedOffers());
        return complete;
    }

    @ModifyArg(
            method = "sendMerchantOffers",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ClientboundMerchantOffersPacket;<init>(ILnet/minecraft/world/item/trading/MerchantOffers;IIZZ)V"),
            index = 2
    )
    private int visibleTraders$packUnlockedOfferCount(int level) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        if (!(player.containerMenu instanceof MerchantMenuDuck menuDuck)) return level;
        if (!(menuDuck.visibleTraders$getTrader() instanceof Villager villager)) return level;
        return level | (VillagerDuck.of(villager).visibleTraders$getAvailableOffersCount() << 8);
    }
}
