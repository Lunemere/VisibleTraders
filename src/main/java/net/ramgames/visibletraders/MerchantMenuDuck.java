package net.ramgames.visibletraders;

import net.minecraft.world.item.trading.Merchant;

public interface MerchantMenuDuck {

    boolean visibleTraders$shouldAllowTrade(int i);

    Merchant visibleTraders$getTrader();
}
