package de.menter.trade;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TradeManager {

    static final long REQUEST_TIMEOUT_MS = 60_000;

    static MinecraftServer server;

    /** target -> (sender -> expiry time) */
    private static final Map<UUID, Map<UUID, Long>> requests = new HashMap<>();
    /** both players of a running trade map to the same session */
    private static final Map<UUID, TradeSession> sessions = new HashMap<>();

    private TradeManager() {}

    static void request(ServerPlayer sender, ServerPlayer target) {
        if (sender == target) {
            fail(sender, "Du kannst nicht mit dir selbst traden.");
            return;
        }
        if (sessions.containsKey(sender.getUUID())) {
            fail(sender, "Du bist schon in einem Trade.");
            return;
        }
        if (sessions.containsKey(target.getUUID())) {
            fail(sender, name(target) + " ist gerade in einem anderen Trade.");
            return;
        }
        // Both asked each other: just start
        if (pending(sender, target)) {
            accept(sender, target);
            return;
        }

        requests.computeIfAbsent(target.getUUID(), k -> new HashMap<>())
                .put(sender.getUUID(), System.currentTimeMillis() + REQUEST_TIMEOUT_MS);

        target.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        target.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.literal(name(sender) + " will mit dir traden").withStyle(ChatFormatting.YELLOW)));
        target.connection.send(new ClientboundSetTitleTextPacket(
                Component.literal("Trade-Anfrage").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)));
        sound(target, SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.2f);

        MutableComponent msg = Component.literal(name(sender) + " will mit dir traden. ").withStyle(ChatFormatting.YELLOW)
                .append(button("[Annehmen]", ChatFormatting.GREEN, "/trade accept " + name(sender), "Trade annehmen"))
                .append(Component.literal(" "))
                .append(button("[Ablehnen]", ChatFormatting.RED, "/trade deny " + name(sender), "Trade ablehnen"));
        target.sendSystemMessage(msg);

        info(sender, "Trade-Anfrage an " + name(target) + " geschickt. Sie gilt 60 Sekunden.");
    }

    static void accept(ServerPlayer self, ServerPlayer sender) {
        if (!pending(self, sender)) {
            fail(self, "Du hast keine offene Trade-Anfrage von " + name(sender) + ".");
            return;
        }
        requests.get(self.getUUID()).remove(sender.getUUID());
        if (sessions.containsKey(self.getUUID()) || sessions.containsKey(sender.getUUID())) {
            fail(self, "Einer von euch ist schon in einem Trade.");
            return;
        }
        if (!self.isAlive() || !sender.isAlive()) {
            fail(self, "Der Trade geht gerade nicht.");
            return;
        }
        TradeSession session = new TradeSession(sender, self);
        sessions.put(sender.getUUID(), session);
        sessions.put(self.getUUID(), session);
        session.open();
    }

    static void deny(ServerPlayer self, ServerPlayer sender) {
        if (!pending(self, sender)) {
            fail(self, "Du hast keine offene Trade-Anfrage von " + name(sender) + ".");
            return;
        }
        requests.get(self.getUUID()).remove(sender.getUUID());
        info(self, "Trade-Anfrage von " + name(sender) + " abgelehnt.");
        fail(sender, name(self) + " hat deine Trade-Anfrage abgelehnt.");
    }

    static void cancelCommand(ServerPlayer self) {
        TradeSession session = sessions.get(self.getUUID());
        if (session != null) {
            session.cancel(name(self) + " hat den Trade abgebrochen.");
            return;
        }
        boolean any = false;
        for (Map<UUID, Long> byTarget : requests.values()) {
            any |= byTarget.remove(self.getUUID()) != null;
        }
        if (any) {
            info(self, "Deine Trade-Anfragen wurden zurückgezogen.");
        } else {
            fail(self, "Du hast keinen laufenden Trade und keine offene Anfrage.");
        }
    }

    static void onLeave(ServerPlayer player, String why) {
        TradeSession session = sessions.get(player.getUUID());
        if (session != null) {
            session.cancel(name(player) + " " + why + ".");
        }
        requests.remove(player.getUUID());
        for (Map<UUID, Long> byTarget : requests.values()) {
            byTarget.remove(player.getUUID());
        }
    }

    static void cancelAll() {
        for (TradeSession session : new ArrayList<>(sessions.values())) {
            session.cancel("Der Server wird gestoppt.");
        }
        requests.clear();
    }

    static void expireRequests() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<UUID, Map<UUID, Long>>> it = requests.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Map<UUID, Long>> entry = it.next();
            List<UUID> expired = new ArrayList<>();
            entry.getValue().forEach((sender, expiry) -> {
                if (expiry < now) expired.add(sender);
            });
            for (UUID senderId : expired) {
                entry.getValue().remove(senderId);
                ServerPlayer sender = server.getPlayerList().getPlayer(senderId);
                ServerPlayer target = server.getPlayerList().getPlayer(entry.getKey());
                if (sender != null && target != null) {
                    fail(sender, "Deine Trade-Anfrage an " + name(target) + " ist abgelaufen.");
                }
            }
            if (entry.getValue().isEmpty()) it.remove();
        }
    }

    static void ended(TradeSession session) {
        sessions.values().removeIf(s -> s == session);
    }

    private static boolean pending(ServerPlayer target, ServerPlayer sender) {
        Map<UUID, Long> byTarget = requests.get(target.getUUID());
        if (byTarget == null) return false;
        Long expiry = byTarget.get(sender.getUUID());
        return expiry != null && expiry >= System.currentTimeMillis();
    }

    private static MutableComponent button(String text, ChatFormatting color, String command, String hover) {
        return Component.literal(text).withStyle(style -> style
                .withColor(color)
                .withBold(true)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    /** only this player hears it */
    static void sound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound), SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(), volume, pitch, player.getRandom().nextLong()));
    }

    static String name(ServerPlayer player) {
        return player.getScoreboardName();
    }

    static void info(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
    }

    static void fail(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.RED));
    }
}
