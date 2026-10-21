package de.menter.trade;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side translations. Clients do not have this mod (and its lang files), so the text is picked
 * here from the language the player set in their client: German for de_*, English for everything else.
 */
public final class Lang {

    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> DE = new HashMap<>();

    static {
        add("error.self", "You can't trade with yourself.", "Du kannst nicht mit dir selbst traden.");
        add("error.busy.self", "You are already in a trade.", "Du bist schon in einem Trade.");
        add("error.busy.other", "%s is in another trade right now.", "%s ist gerade in einem anderen Trade.");
        add("error.busy.any", "One of you is already in a trade.", "Einer von euch ist schon in einem Trade.");
        add("error.unavailable", "Trading is not possible right now.", "Der Trade geht gerade nicht.");
        add("error.no_request", "You have no open trade request from %s.", "Du hast keine offene Trade-Anfrage von %s.");
        add("error.nothing", "You have no running trade and no open request.", "Du hast keinen laufenden Trade und keine offene Anfrage.");
        add("error.too_far", "%s is too far away (max. %s blocks).", "%s ist zu weit weg (max. %s Blöcke).");
        add("error.dimension", "%s is in another dimension.", "%s ist in einer anderen Dimension.");

        add("request.title", "Trade request", "Trade-Anfrage");
        add("request.subtitle", "%s wants to trade with you", "%s will mit dir traden");
        add("request.chat", "%s wants to trade with you. ", "%s will mit dir traden. ");
        add("request.accept", "[Accept]", "[Annehmen]");
        add("request.accept.hover", "Accept the trade", "Trade annehmen");
        add("request.deny", "[Deny]", "[Ablehnen]");
        add("request.deny.hover", "Deny the trade", "Trade ablehnen");
        add("request.sent", "Trade request sent to %s. It is valid for %s seconds.", "Trade-Anfrage an %s geschickt. Sie gilt %s Sekunden.");
        add("request.denied.self", "Denied the trade request from %s.", "Trade-Anfrage von %s abgelehnt.");
        add("request.denied.other", "%s denied your trade request.", "%s hat deine Trade-Anfrage abgelehnt.");
        add("request.withdrawn", "Your trade requests were withdrawn.", "Deine Trade-Anfragen wurden zurückgezogen.");
        add("request.expired", "Your trade request to %s has expired.", "Deine Trade-Anfrage an %s ist abgelaufen.");

        add("reason.cancelled", "%s cancelled the trade.", "%s hat den Trade abgebrochen.");
        add("reason.closed", "%s closed the trade window.", "%s hat das Trade-Fenster geschlossen.");
        add("reason.left", "%s left the server.", "%s hat den Server verlassen.");
        add("reason.died", "%s died.", "%s ist gestorben.");
        add("reason.stopping", "The server is stopping.", "Der Server wird gestoppt.");
        add("reason.open_failed", "The trade window could not be opened.", "Das Trade-Fenster konnte nicht geöffnet werden.");
        add("reason.complete_failed", "The trade could not be completed.", "Der Trade konnte nicht abgeschlossen werden.");
        add("trade.cancelled", "Trade cancelled: %s You got your items back.", "Trade abgebrochen: %s Du hast deine Sachen zurück.");
        add("trade.completed", "Trade with %s completed!", "Trade mit %s abgeschlossen!");
        add("trade.recovered", "You got back the items from an interrupted trade.", "Du hast die Sachen aus einem unterbrochenen Trade zurückbekommen.");

        add("trade.changed", "Offer changed – please confirm again", "Angebot geändert – bitte neu bestätigen");
        add("trade.empty", "Put something into the trade first", "Legt erst etwas in den Trade");
        add("trade.cooldown", "The offer just changed – take another look", "Das Angebot hat sich gerade geändert – schau nochmal drüber");

        add("menu.title", "You  ⇄  %s", "Du  ⇄  %s");
        add("menu.own", "← Your items", "← Deine Sachen");
        add("menu.own.lore", "Put what you want to give on the left.", "Leg links rein, was du geben willst.");
        add("menu.partner", "%s's items →", "%ss Sachen →");
        add("menu.partner.lore", "This is what you get from %s.", "Das bekommst du von %s.");
        add("menu.cancel", "Cancel", "Abbrechen");
        add("menu.cancel.lore", "Everyone gets their items back.", "Jeder bekommt seine Sachen zurück.");
        add("menu.confirm", "Confirm", "Bestätigen");
        add("menu.confirm.lore1", "Click when you agree with the trade.", "Klicke, wenn du mit dem Tausch einverstanden bist.");
        add("menu.confirm.lore2", "Items are only swapped when both confirmed.", "Getauscht wird erst, wenn beide bestätigt haben.");
        add("menu.confirmed", "✔ You confirmed", "✔ Du hast bestätigt");
        add("menu.confirmed.lore", "Click to take it back.", "Klicken zum Zurücknehmen.");
        add("menu.partner_confirmed", "✔ %s confirmed", "✔ %s hat bestätigt");
        add("menu.partner_waiting", "%s has not confirmed yet", "%s hat noch nicht bestätigt");

        add("log.title", "Trade log", "Trade-Log");
        add("log.title.player", "Trade log: %s", "Trade-Log: %s");
        add("log.entry", "#%s  %s ⇄ %s", "#%s  %s ⇄ %s");
        add("log.date_format", "yyyy-MM-dd HH:mm", "dd.MM.yyyy HH:mm");
        add("log.gave", "%s gave:", "%s hat gegeben:");
        add("log.nothing", "  nothing", "  nichts");
        add("log.more", "  … and %s more", "  … und %s weitere");
        add("log.open", "Click to view", "Klicken zum Ansehen");
        add("log.prev", "← Previous page", "← Vorherige Seite");
        add("log.next", "Next page →", "Nächste Seite →");
        add("log.page", "Page %s of %s", "Seite %s von %s");
        add("log.count", "%s trades", "%s Trades");
        add("log.filter", "Player: %s", "Spieler: %s");
        add("log.empty", "No trades logged yet.", "Noch keine Trades geloggt.");
        add("log.empty.player", "No logged trades with %s.", "Keine geloggten Trades mit %s.");
        add("log.back", "← Back to the list", "← Zurück zur Liste");
        add("log.label.a", "← %s gave", "← %s hat gegeben");
        add("log.label.b", "%s gave →", "%s hat gegeben →");
        add("log.label.got", "%s got this.", "Das hat %s bekommen.");
        add("log.disabled", "Trade logging is turned off (log_trades in config/handshake-trade.json).",
                "Das Trade-Log ist ausgeschaltet (log_trades in config/handshake-trade.json).");
    }

    private Lang() {}

    private static void add(String key, String en, String de) {
        EN.put(key, en);
        DE.put(key, de);
    }

    public static String get(ServerPlayer player, String key, Object... args) {
        Map<String, String> table = isGerman(player) ? DE : EN;
        String pattern = table.getOrDefault(key, EN.getOrDefault(key, key));
        return args.length == 0 ? pattern : String.format(pattern, args);
    }

    private static boolean isGerman(ServerPlayer player) {
        String language = player.clientInformation().language();
        return language != null && language.startsWith("de");
    }
}
