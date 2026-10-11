package net.ramgames.visibletraders;

import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.entity.npc.Villager;

public interface VillagerDuck {

    int visibleTraders$getAvailableOffersCount();

    MerchantOffers visibleTraders$getLockedOffers();

    static VillagerDuck of(Villager villager) {
        return (VillagerDuck) villager;
    }
}
