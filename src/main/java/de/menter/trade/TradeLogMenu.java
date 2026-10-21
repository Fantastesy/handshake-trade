package de.menter.trade;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Read-only trade log for operators, again a plain 6-row chest. The list shows 45 trades per page (newest
 * first); clicking one shows it in the same layout as the trade window: left what the first player gave,
 * right what the second player gave.
 */
public class TradeLogMenu extends AbstractContainerMenu {

    private static final int ROWS = 6;
    private static final int TOP_SIZE = ROWS * 9;
    private static final int PAGE_SIZE = 45;
    private static final int SUMMARY_LINES = 4;

    private static final int PREV = 45;
    private static final int INFO = 49;
    private static final int NEXT = 53;

    private static final int LABEL_A = 45;
    private static final int TIME = 47;
    private static final int BACK = 49;
    private static final int ID = 51;
    private static final int LABEL_B = 53;

    private final ServerPlayer viewer;
    private final String filter;
    private final List<TradeLog.Entry> entries;
    private final SimpleContainer view = new SimpleContainer(TOP_SIZE);
    private int page;
    /** null while the list is shown */
    private TradeLog.Entry selected;

    TradeLogMenu(int containerId, Inventory inventory, ServerPlayer viewer, String filter) {
        super(MenuType.GENERIC_9x6, containerId);
        this.viewer = viewer;
        this.filter = filter;
        this.entries = TradeLog.list(filter);
        for (int i = 0; i < TOP_SIZE; i++) {
            addSlot(new ViewSlot(view, i));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 0, 0));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 0, 0));
        }
        draw();
    }

    static Component title(ServerPlayer viewer, String filter) {
        return Component.literal(filter == null ? Lang.get(viewer, "log.title") : Lang.get(viewer, "log.title.player", filter));
    }

    private int pages() {
        return Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    private void draw() {
        view.clearContent();
        if (selected == null) drawList(); else drawTrade(selected);
        broadcastChanges();
    }

    private void drawList() {
        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < entries.size(); i++) {
            view.setItem(i, entryItem(entries.get(start + i)));
        }
        for (int i = PAGE_SIZE; i < TOP_SIZE; i++) {
            view.setItem(i, named(Items.STAINED_GLASS_PANE.gray(), Component.literal(" ")));
        }
        if (page > 0) {
            view.setItem(PREV, named(Items.ARROW, text(tr("log.prev"), ChatFormatting.YELLOW)));
        }
        if (page < pages() - 1) {
            view.setItem(NEXT, named(Items.ARROW, text(tr("log.next"), ChatFormatting.YELLOW)));
        }
        List<String> lore = new ArrayList<>();
        lore.add(tr("log.count", entries.size()));
        if (filter != null) lore.add(tr("log.filter", filter));
        if (entries.isEmpty()) {
            lore.add(filter == null ? tr("log.empty") : tr("log.empty.player", filter));
        }
        view.setItem(INFO, named(Items.BOOK, text(tr("log.page", page + 1, pages()), ChatFormatting.AQUA),
                lore.toArray(String[]::new)));
    }

    private ItemStack entryItem(TradeLog.Entry entry) {
        List<Component> lore = new ArrayList<>();
        lore.add(gray(time(entry)));
        summary(lore, entry.nameA(), entry.fromA());
        summary(lore, entry.nameB(), entry.fromB());
        lore.add(Component.literal(tr("log.open")).withStyle(style -> style.withColor(ChatFormatting.YELLOW).withItalic(false)));
        ItemStack stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_NAME,
                text(tr("log.entry", entry.id(), entry.nameA(), entry.nameB()), ChatFormatting.GOLD));
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private void summary(List<Component> lore, String name, List<ItemStack> items) {
        lore.add(Component.literal(tr("log.gave", name)).withStyle(style -> style.withColor(ChatFormatting.AQUA).withItalic(false)));
        if (items.isEmpty()) {
            lore.add(gray(tr("log.nothing")));
            return;
        }
        for (int i = 0; i < Math.min(SUMMARY_LINES, items.size()); i++) {
            ItemStack stack = items.get(i);
            // the item name stays a translatable component, so the client shows it in its own language
            lore.add(Component.literal("  " + stack.getCount() + "× ").append(stack.getHoverName())
                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)));
        }
        if (items.size() > SUMMARY_LINES) {
            lore.add(gray(tr("log.more", items.size() - SUMMARY_LINES)));
        }
    }

    private void drawTrade(TradeLog.Entry entry) {
        for (int i = 0; i < TOP_SIZE; i++) {
            int row = i / 9;
            int col = i % 9;
            if (row < 5 && col < 4) {
                int index = row * 4 + col;
                if (index < entry.fromA().size()) view.setItem(i, entry.fromA().get(index).copy());
            } else if (row < 5 && col > 4) {
                int index = row * 4 + (col - 5);
                if (index < entry.fromB().size()) view.setItem(i, entry.fromB().get(index).copy());
            } else if (row < 5) {
                view.setItem(i, named(Items.STAINED_GLASS_PANE.black(), Component.literal(" ")));
            } else {
                view.setItem(i, named(Items.STAINED_GLASS_PANE.gray(), Component.literal(" ")));
            }
        }
        view.setItem(LABEL_A, named(Items.OAK_SIGN, text(tr("log.label.a", entry.nameA()), ChatFormatting.AQUA),
                tr("log.label.got", entry.nameB())));
        view.setItem(LABEL_B, named(Items.OAK_SIGN, text(tr("log.label.b", entry.nameB()), ChatFormatting.AQUA),
                tr("log.label.got", entry.nameA())));
        view.setItem(TIME, named(Items.CLOCK, text(time(entry), ChatFormatting.WHITE)));
        view.setItem(BACK, named(Items.ARROW, text(tr("log.back"), ChatFormatting.YELLOW)));
        view.setItem(ID, named(Items.PAPER, text(tr("log.entry", entry.id(), entry.nameA(), entry.nameB()), ChatFormatting.GOLD)));
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput clickType, Player player) {
        if (slotId >= 0 && slotId < TOP_SIZE) {
            if (clickType == ContainerInput.PICKUP) click(slotId);
            // nothing up here can be taken; the client gets corrected by the next sync
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    private void click(int slotId) {
        if (selected != null) {
            if (slotId == BACK) {
                selected = null;
                draw();
            }
            return;
        }
        if (slotId == PREV && page > 0) {
            page--;
            draw();
        } else if (slotId == NEXT && page < pages() - 1) {
            page++;
            draw();
        } else if (slotId < PAGE_SIZE) {
            int index = page * PAGE_SIZE + slotId;
            if (index < entries.size()) {
                selected = entries.get(index);
                draw();
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
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
        return player.isAlive();
    }

    private String time(TradeLog.Entry entry) {
        return TradeLog.formatTime(entry.time(), DateTimeFormatter.ofPattern(tr("log.date_format"), Locale.ROOT));
    }

    private String tr(String key, Object... args) {
        return Lang.get(viewer, key, args);
    }

    private static Component gray(String s) {
        return Component.literal(s).withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false));
    }

    private static Component text(String s, ChatFormatting color) {
        return Component.literal(s).withStyle(style -> style.withColor(color).withItalic(false));
    }

    private static ItemStack named(Item item, Component name, String... lore) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, name);
        if (lore.length > 0) {
            stack.set(DataComponents.LORE, new ItemLore(Arrays.stream(lore).map(TradeLogMenu::gray).toList()));
        }
        return stack;
    }

    /** shows items but can never be taken from or put into */
    private static class ViewSlot extends Slot {
        ViewSlot(SimpleContainer container, int index) {
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
