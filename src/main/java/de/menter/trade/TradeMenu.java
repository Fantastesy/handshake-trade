package de.menter.trade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.Arrays;

/**
 * Server-side trade window shown to the client as a plain 6-row chest, so players need no mod.
 * <pre>
 * cols 0-3, rows 0-4: own offer (editable)   col 4: divider   cols 5-8, rows 0-4: partner's offer (read-only)
 * row 5: own label, confirm button, cancel button, partner status, partner label
 * </pre>
 */
public class TradeMenu extends AbstractContainerMenu {

    private static final int ROWS = 6;
    private static final int TOP_SIZE = ROWS * 9;
    private static final int INV_START = TOP_SIZE;
    private static final int INV_END = TOP_SIZE + 36;

    private static final int OWN_LABEL = 45;
    private static final int CONFIRM = 47;
    private static final int CANCEL = 49;
    private static final int PARTNER_STATUS = 51;
    private static final int PARTNER_LABEL = 53;

    private final TradeSession session;
    private final ServerPlayer owner;
    private final SimpleContainer deco = new SimpleContainer(TOP_SIZE);
    /** set while this menu is being closed, so the session does not try to close it a second time */
    boolean closing;

    TradeMenu(int containerId, Inventory inventory, TradeSession session, ServerPlayer owner) {
        super(MenuType.GENERIC_9x6, containerId);
        this.session = session;
        this.owner = owner;
        SimpleContainer own = session.offerOf(owner);
        SimpleContainer partner = session.offerOf(session.partnerOf(owner));

        for (int i = 0; i < TOP_SIZE; i++) {
            int row = i / 9;
            int col = i % 9;
            if (isOwnOffer(i)) {
                addSlot(new Slot(own, row * 4 + col, 0, 0));
            } else if (row < 5 && col > 4) {
                addSlot(new ViewSlot(partner, row * 4 + (col - 5)));
            } else {
                addSlot(new ViewSlot(deco, i));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 0, 0));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 0, 0));
        }
    }

    private static boolean isOwnOffer(int slotId) {
        return slotId >= 0 && slotId < TOP_SIZE && slotId / 9 < 5 && slotId % 9 < 4;
    }

    void refreshDeco() {
        ServerPlayer partner = session.partnerOf(owner);
        String partnerName = TradeManager.name(partner);
        for (int i = 0; i < TOP_SIZE; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row < 5 && col == 4) {
                deco.setItem(i, named(Items.STAINED_GLASS_PANE.black(), Component.literal(" ")));
            } else if (row == 5) {
                deco.setItem(i, named(Items.STAINED_GLASS_PANE.gray(), Component.literal(" ")));
            }
        }
        deco.setItem(OWN_LABEL, named(Items.OAK_SIGN, text(tr("menu.own"), ChatFormatting.AQUA),
                tr("menu.own.lore")));
        deco.setItem(PARTNER_LABEL, named(Items.OAK_SIGN, text(tr("menu.partner", partnerName), ChatFormatting.AQUA),
                tr("menu.partner.lore", partnerName)));
        deco.setItem(CANCEL, named(Items.BARRIER, text(tr("menu.cancel"), ChatFormatting.RED),
                tr("menu.cancel.lore")));
        if (session.isConfirmed(owner)) {
            deco.setItem(CONFIRM, named(Items.CONCRETE.lime(), text(tr("menu.confirmed"), ChatFormatting.GREEN),
                    tr("menu.confirmed.lore")));
        } else {
            deco.setItem(CONFIRM, named(Items.DYE.lime(), text(tr("menu.confirm"), ChatFormatting.GREEN),
                    tr("menu.confirm.lore1"), tr("menu.confirm.lore2")));
        }
        if (session.isConfirmed(partner)) {
            deco.setItem(PARTNER_STATUS, named(Items.STAINED_GLASS_PANE.lime(),
                    text(tr("menu.partner_confirmed", partnerName), ChatFormatting.GREEN)));
        } else {
            deco.setItem(PARTNER_STATUS, named(Items.STAINED_GLASS_PANE.red(),
                    text(tr("menu.partner_waiting", partnerName), ChatFormatting.RED)));
        }
        broadcastChanges();
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (session.ended) return;
        if (slotId >= 0 && slotId < TOP_SIZE && !isOwnOffer(slotId)) {
            if (clickType == ContainerInput.PICKUP) {
                if (slotId == CONFIRM) {
                    session.toggleConfirm(owner);
                } else if (slotId == CANCEL) {
                    TradeManager.server.execute(() -> session.cancel("reason.cancelled", TradeManager.name(owner)));
                }
            }
            // everything else in the read-only area is ignored; the client gets corrected by the next sync
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (session.ended || !slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (isOwnOffer(index)) {
            if (!moveItemStackTo(stack, INV_START, INV_END, true)) return ItemStack.EMPTY;
        } else if (index >= INV_START) {
            if (!moveToOwnOffer(stack)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /** own offer slots are not one contiguous index range, so moveItemStackTo cannot be used */
    private boolean moveToOwnOffer(ItemStack stack) {
        boolean moved = false;
        for (int i = 0; i < TOP_SIZE && !stack.isEmpty(); i++) {
            if (!isOwnOffer(i)) continue;
            Slot target = slots.get(i);
            ItemStack existing = target.getItem();
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int space = target.getMaxStackSize(existing) - existing.getCount();
                if (space > 0) {
                    int move = Math.min(space, stack.getCount());
                    existing.grow(move);
                    stack.shrink(move);
                    target.setChanged();
                    moved = true;
                }
            }
        }
        for (int i = 0; i < TOP_SIZE && !stack.isEmpty(); i++) {
            if (!isOwnOffer(i)) continue;
            Slot target = slots.get(i);
            if (!target.hasItem() && target.mayPlace(stack)) {
                target.setByPlayer(stack.split(Math.min(stack.getCount(), target.getMaxStackSize(stack))));
                moved = true;
            }
        }
        return moved;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof ViewSlot) && super.canTakeItemForPickAll(stack, slot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof ViewSlot) && super.canDragTo(slot);
    }

    @Override
    public boolean stillValid(Player player) {
        return !session.ended && player.isAlive();
    }

    @Override
    public void removed(Player player) {
        closing = true;
        super.removed(player);
        if (!session.ended) {
            session.cancel("reason.closed", TradeManager.name(owner));
        }
    }

    private String tr(String key, Object... args) {
        return Lang.get(owner, key, args);
    }

    private static Component text(String s, ChatFormatting color) {
        return Component.literal(s).withStyle(style -> style.withColor(color).withItalic(false));
    }

    private static ItemStack named(Item item, Component name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (lore.length > 0) {
            stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore)
                    .<Component>map(l -> Component.literal(l).withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)))
                    .toList()));
        }
        return stack;
    }

    /** shows items but can never be taken from or put into */
    private static class ViewSlot extends Slot {
        ViewSlot(Container container, int index) {
            super(container, index, 0, 0);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }
    }
}
