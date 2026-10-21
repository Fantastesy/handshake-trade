package de.menter.trade;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Crash safety: whatever a player has put into a running trade is mirrored into a persistent attachment
 * on that player. It is saved in the same player file as the inventory, so after a crash every item is
 * either still in the inventory or in this list, never in both. On the next join the list is handed back.
 */
public final class TradeRecovery {

    static final AttachmentType<List<ItemStack>> PENDING = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(TradeMod.MOD_ID, "pending_offer"),
            builder -> builder.persistent(ItemStack.CODEC.listOf()));

    private TradeRecovery() {}

    static void mirror(ServerPlayer player, SimpleContainer offer) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < offer.getContainerSize(); i++) {
            ItemStack stack = offer.getItem(i);
            if (!stack.isEmpty()) items.add(stack.copy());
        }
        if (items.isEmpty()) {
            clear(player);
        } else {
            player.setAttached(PENDING, items);
        }
    }

    static void clear(ServerPlayer player) {
        player.removeAttached(PENDING);
    }

    static void restoreOnJoin(ServerPlayer player) {
        List<ItemStack> items = player.getAttached(PENDING);
        if (items == null) return;
        player.removeAttached(PENDING);
        for (ItemStack stack : items) {
            player.getInventory().placeItemBackInInventory(stack.copy(), Prediction.SERVER_ONLY);
        }
        player.sendSystemMessage(Component.literal(Lang.get(player, "trade.recovered")).withStyle(ChatFormatting.GREEN));
    }
}
