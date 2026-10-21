package de.menter.trade;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Every completed trade is appended to {@code <world>/handshake-trade/trades.jsonl} (full item data, read back
 * for the in-game viewer) and to {@code trades.log} (one readable line per trade, for admins without the game).
 */
public final class TradeLog {

    private static final Logger LOGGER = LoggerFactory.getLogger("handshake-trade");
    private static final DateTimeFormatter TEXT_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    public record Entry(int id, long time, String nameA, UUID uuidA, String nameB, UUID uuidB,
                        List<ItemStack> fromA, List<ItemStack> fromB) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("id").forGetter(Entry::id),
                Codec.LONG.fieldOf("time").forGetter(Entry::time),
                Codec.STRING.fieldOf("player_a").forGetter(Entry::nameA),
                UUIDUtil.STRING_CODEC.fieldOf("uuid_a").forGetter(Entry::uuidA),
                Codec.STRING.fieldOf("player_b").forGetter(Entry::nameB),
                UUIDUtil.STRING_CODEC.fieldOf("uuid_b").forGetter(Entry::uuidB),
                ItemStack.CODEC.listOf().fieldOf("from_a").forGetter(Entry::fromA),
                ItemStack.CODEC.listOf().fieldOf("from_b").forGetter(Entry::fromB)
        ).apply(i, Entry::new));

        boolean involves(String name) {
            return nameA.equalsIgnoreCase(name) || nameB.equalsIgnoreCase(name);
        }
    }

    /** oldest first */
    private static final List<Entry> entries = new ArrayList<>();
    private static Path dir;

    private TradeLog() {}

    static void load(MinecraftServer server) {
        entries.clear();
        dir = server.getWorldPath(LevelResource.ROOT).resolve(TradeMod.MOD_ID);
        Path file = dir.resolve("trades.jsonl");
        if (!Files.exists(file)) return;
        RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNo = 0;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                if (line.isBlank()) continue;
                int n = lineNo;
                try {
                    Entry.CODEC.parse(ops, JsonParser.parseString(line))
                            .resultOrPartial(error -> LOGGER.warn("trades.jsonl line {}: {}", n, error))
                            .ifPresent(entries::add);
                } catch (RuntimeException e) {
                    LOGGER.warn("trades.jsonl line {} is broken, skipped", n, e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("Could not read {}", file, e);
        }
        LOGGER.info("Loaded {} logged trades", entries.size());
    }

    static void record(MinecraftServer server, ServerPlayer a, ServerPlayer b, List<ItemStack> fromA, List<ItemStack> fromB) {
        if (!TradeConfig.get().logTrades || dir == null) return;
        int id = entries.isEmpty() ? 1 : entries.getLast().id() + 1;
        Entry entry = new Entry(id, System.currentTimeMillis(), TradeManager.name(a), a.getUUID(),
                TradeManager.name(b), b.getUUID(), copy(fromA), copy(fromB));
        entries.add(entry);

        String text = String.format("#%d %s  %s gave [%s]  <->  %s gave [%s]", id, formatTime(entry.time(), TEXT_TIME),
                entry.nameA(), describe(entry.fromA()), entry.nameB(), describe(entry.fromB()));
        LOGGER.info("Trade {}", text);
        try {
            Files.createDirectories(dir);
            RegistryOps<JsonElement> ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            JsonElement json = Entry.CODEC.encodeStart(ops, entry).getOrThrow();
            Files.writeString(dir.resolve("trades.jsonl"), json + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            Files.writeString(dir.resolve("trades.log"), text + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("Could not write the trade log", e);
        }
    }

    /** newest first; {@code player} null = all trades */
    static List<Entry> list(String player) {
        List<Entry> result = new ArrayList<>();
        for (Entry entry : entries) {
            if (player == null || entry.involves(player)) result.add(entry);
        }
        Collections.reverse(result);
        return result;
    }

    /** everyone who appears in the log, for the command suggestions */
    static Set<String> names() {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Entry entry : entries) {
            names.add(entry.nameA());
            names.add(entry.nameB());
        }
        return names;
    }

    static String formatTime(long time, DateTimeFormatter format) {
        return format.format(Instant.ofEpochMilli(time).atZone(zone()));
    }

    private static ZoneId zone() {
        String configured = TradeConfig.get().timeZone;
        if (configured != null && !configured.isBlank()) {
            try {
                return ZoneId.of(configured);
            } catch (RuntimeException ignored) {
                // invalid id: fall back to the server's zone
            }
        }
        return ZoneId.systemDefault();
    }

    private static String describe(List<ItemStack> items) {
        return items.stream()
                .map(s -> s.getCount() + "x " + BuiltInRegistries.ITEM.getKey(s.getItem()))
                .collect(Collectors.joining(", "));
    }

    private static List<ItemStack> copy(List<ItemStack> items) {
        return items.stream().map(ItemStack::copy).toList();
    }
}
