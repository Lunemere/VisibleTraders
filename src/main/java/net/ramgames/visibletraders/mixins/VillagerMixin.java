package net.ramgames.visibletraders.mixins;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ReputationEventHandler;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.ramgames.visibletraders.VillagerDuck;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(Villager.class)
public abstract class VillagerMixin extends AbstractVillager implements ReputationEventHandler, VillagerDataHolder, VillagerDuck {

    @Shadow public abstract @NotNull VillagerData getVillagerData();

    @Shadow public abstract void setVillagerData(VillagerData villagerData);

    @Shadow public abstract void updateTrades();

    @Shadow public abstract boolean isClientSide();

    @Shadow public abstract void onReputationEventFrom(ReputationEventType reputationEventType, Entity entity);

    @Unique
    private static final Logger visibleTradersLogger = LoggerFactory.getLogger("Visible Traders");

    @Unique
    private List<MerchantOffers> lockedOffers = null;

    @Unique
    private MerchantOffer cachedTrade = null;

    @Unique
    private int prevLevel = 0;

    public VillagerMixin(EntityType<? extends AbstractVillager> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "addAdditionalSaveData", at = @At("HEAD"))
    private void writeOfferingLevel(CompoundTag compoundTag, CallbackInfo ci) {
        if(this.lockedOffers == null) return;
        ListTag serializedOffers = new ListTag();
        for (MerchantOffers offers : this.lockedOffers) {
            serializedOffers.add(offers.createTag());
        }
        compoundTag.put("LockedOffers", serializedOffers);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void updateLockedTradesOnTick(CallbackInfo ci) {
        if(this.isClientSide()) return;

        int level = this.getVillagerData().getLevel();
        prevLevel = level;

        if(this.offers == null) {
            // Trade Cycling temporarily clears the villager's normal offers
            // before immediately sending the refreshed merchant menu. Keep
            // the cached locked offers available so that packet still
            // contains the complete trade list.
            return;
        }

        if(cachedTrade == null || this.offers.isEmpty()) {
            this.lockedOffers = null;
            if(!this.offers.isEmpty()) this.cachedTrade = this.offers.get(0);
            return;
        }

        if(cachedTrade != this.offers.get(0)) {
            this.lockedOffers = null;
            this.cachedTrade = this.offers.get(0);
            return;
        }

        if(this.lockedOffers == null) this.lockedOffers = new ArrayList<>();
        int size = this.lockedOffers.size();

        if(size + level == 5) return;
        if(size > 0 && size + level > 5) {
            this.lockedOffers.remove(0);
            return;
        }
        VillagerData data = this.getVillagerData();
        this.setVillagerData(data.setLevel(data.getLevel()+size + 1));
        int prev = this.offers.size();
        this.updateTrades();
        int dif = this.offers.size() - prev;
        MerchantOffers newOffers = new MerchantOffers();
        for(int i = 0; i < dif; i++) newOffers.add(this.offers.remove(this.offers.size() - 1));
        this.lockedOffers.add(newOffers);
        this.setVillagerData(data);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readOfferingLevel(CompoundTag compoundTag, CallbackInfo ci) {
        this.lockedOffers = new ArrayList<>();
        if(compoundTag.contains("LockedOffers", Tag.TAG_LIST)) {
            ListTag serializedOffers = compoundTag.getList("LockedOffers", Tag.TAG_COMPOUND);
            for (int i = 0; i < serializedOffers.size(); i++) {
                this.lockedOffers.add(new MerchantOffers(serializedOffers.getCompound(i)));
            }
        }
        if(this.offers != null && !this.offers.isEmpty()) cachedTrade = this.offers.get(0);
    }

    @Inject(method = "updateTrades", at = @At("HEAD"), cancellable = true)
    private void preventAdditionalTradesOnRankIncrease(CallbackInfo ci) {
        if(this.offers == null) return;
        if(this.lockedOffers == null) return;
        if(prevLevel != this.getVillagerData().getLevel()) return;
        if(!this.lockedOffers.isEmpty() && this.lockedOffers.size() + this.getVillagerData().getLevel() > 5) {
            MerchantOffers newOffers = lockedOffers.remove(0);
            this.offers.addAll(newOffers);
            ci.cancel();
        }
    }

    @Inject(method = "increaseMerchantCareer", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/npc/Villager;updateTrades()V"))
    private void updateLastKnownLevelOnCareerIncrease(CallbackInfo ci) {
        this.prevLevel = this.getVillagerData().getLevel();
    }

    @Override
    public int visibleTraders$getAvailableOffersCount() {
        if(this.offers == null) return 0;
        return this.offers.size();
    }

    @Override
    public MerchantOffers visibleTraders$getLockedOffers() {
        if(this.lockedOffers == null) return new MerchantOffers();
        MerchantOffers lockedOffers = new MerchantOffers();
        for(MerchantOffers listOffers : List.copyOf(this.lockedOffers)) for(MerchantOffer offer : listOffers) {
            if(offer.getResult().isEmpty()) {
                this.lockedOffers = new ArrayList<>();
                visibleTradersLogger.error("detected incomplete trade. Rebuilding locked offers");
                return new MerchantOffers();
            }
            lockedOffers.add(offer);
        }
        return lockedOffers;
    }
}
