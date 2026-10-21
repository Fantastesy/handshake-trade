package de.menter.trade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * One running trade between two players. Each player has an offer container that only they can edit;
 * the partner's menu shows it read-only. Any change to an offer withdraws both confirmations.
 */
public final class TradeSession {

    static final int OFFER_SIZE = 20;
    /** confirming right after the other side changed something is blocked, so nobody gets tricked by a last-second swap */
    static final long CONFIRM_COOLDOWN_MS = 1500;

    final ServerPlayer a;
    final ServerPlayer b;
    private final SimpleContainer offerA = new OfferContainer();
    private final SimpleContainer offerB = new OfferContainer();
    private boolean confirmedA;
    private boolean confirmedB;
    private long lastChange;
    private TradeMenu menuA;
    private TradeMenu menuB;
    boolean ended;

    TradeSession(ServerPlayer a, ServerPlayer b) {
        this.a = a;
        this.b = b;
    }

    void open() {
        a.openMenu(new SimpleMenuProvider((id, inventory, player) -> menuA = new TradeMenu(id, inventory, this, a),
                Component.literal("Du  ⇄  " + TradeManager.name(b))));
        b.openMenu(new SimpleMenuProvider((id, inventory, player) -> menuB = new TradeMenu(id, inventory, this, b),
                Component.literal("Du  ⇄  " + TradeManager.name(a))));
        if (menuA == null || menuB == null || a.containerMenu != menuA || b.containerMenu != menuB) {
            cancel("Das Trade-Fenster konnte nicht geöffnet werden.");
            return;
        }
        lastChange = System.currentTimeMillis();
        refresh();
        TradeManager.sound(a, SoundEvents.CHEST_OPEN, 0.6f, 1.2f);
        TradeManager.sound(b, SoundEvents.CHEST_OPEN, 0.6f, 1.2f);
    }

    SimpleContainer offerOf(ServerPlayer player) {
        return player == a ? offerA : offerB;
    }

    ServerPlayer partnerOf(ServerPlayer player) {
        return player == a ? b : a;
    }

    boolean isConfirmed(ServerPlayer player) {
        return player == a ? confirmedA : confirmedB;
    }

    private void onOfferChanged() {
        if (ended) return;
        lastChange = System.currentTimeMillis();
        if (confirmedA || confirmedB) {
            confirmedA = false;
            confirmedB = false;
            actionbar(a, "Angebot geändert – bitte neu bestätigen");
            actionbar(b, "Angebot geändert – bitte neu bestätigen");
        }
        refresh();
    }

    void toggleConfirm(ServerPlayer player) {
        if (ended) return;
        boolean now = !isConfirmed(player);
        if (now) {
            if (offerA.isEmpty() && offerB.isEmpty()) {
                actionbar(player, "Legt erst etwas in den Trade");
                return;
            }
            if (System.currentTimeMillis() - lastChange < CONFIRM_COOLDOWN_MS) {
                actionbar(player, "Das Angebot hat sich gerade geändert – schau nochmal drüber");
                return;
            }
        }
        if (player == a) confirmedA = now; else confirmedB = now;
        TradeManager.sound(player, now ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, now ? 1.4f : 0.8f);
        refresh();
        if (confirmedA && confirmedB) {
            // Finish outside of the click handling; recheck there in case something changed in between
            TradeManager.server.execute(() -> {
                if (!ended && confirmedA && confirmedB) complete();
            });
        }
    }

    private void complete() {
        if (a.hasDisconnected() || b.hasDisconnected() || !a.isAlive() || !b.isAlive()) {
            cancel("Der Trade konnte nicht abgeschlossen werden.");
            return;
        }
        ended = true;
        TradeManager.ended(this);
        closeMenus();
        List<ItemStack> fromA = takeAll(offerA);
        List<ItemStack> fromB = takeAll(offerB);
        give(b, fromA);
        give(a, fromB);
        for (ServerPlayer p : List.of(a, b)) {
            p.sendSystemMessage(Component.literal("Trade mit " + TradeManager.name(partnerOf(p)) + " abgeschlossen!")
                    .withStyle(ChatFormatting.GREEN));
            TradeManager.sound(p, SoundEvents.PLAYER_LEVELUP, 0.7f, 1.3f);
        }
    }

    void cancel(String reason) {
        if (ended) return;
        ended = true;
        TradeManager.ended(this);
        closeMenus();
        give(a, takeAll(offerA));
        give(b, takeAll(offerB));
        for (ServerPlayer p : List.of(a, b)) {
            if (!p.hasDisconnected()) {
                TradeManager.fail(p, "Trade abgebrochen: " + reason + " Du hast deine Sachen zurück.");
            }
        }
    }

    private void closeMenus() {
        // closeContainer() runs TradeMenu.removed(), which hands back the item on the cursor
        if (menuA != null && a.containerMenu == menuA && !menuA.closing) a.closeContainer();
        if (menuB != null && b.containerMenu == menuB && !menuB.closing) b.closeContainer();
    }

    private static List<ItemStack> takeAll(SimpleContainer offer) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < offer.getContainerSize(); i++) {
            ItemStack stack = offer.removeItemNoUpdate(i);
            if (!stack.isEmpty()) items.add(stack);
        }
        return items;
    }

    /** into the inventory; whatever does not fit is dropped at the player's feet */
    private static void give(ServerPlayer player, List<ItemStack> items) {
        for (ItemStack stack : items) {
            player.getInventory().placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
        }
    }

    private void refresh() {
        if (menuA != null) menuA.refreshDeco();
        if (menuB != null) menuB.refreshDeco();
    }

    /** SimpleContainer lost its listeners in 26.x; every slot edit ends in setChanged() */
    private final class OfferContainer extends SimpleContainer {
        OfferContainer() {
            super(OFFER_SIZE);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            onOfferChanged();
        }
    }

    private static void actionbar(ServerPlayer player, String text) {
        player.sendOverlayMessage(Component.literal(text).withStyle(ChatFormatting.YELLOW));
    }
}
