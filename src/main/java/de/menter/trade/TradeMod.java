package de.menter.trade;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

public class TradeMod implements ModInitializer {

    public static final String MOD_ID = "handshake-trade";

    @Override
    public void onInitialize() {
        TradeConfig.load();
        // touch the class so the attachment type is registered before any player data is loaded
        TradeRecovery.PENDING.identifier();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("trade")
                        .then(Commands.literal("accept")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> run(ctx, Action.ACCEPT))))
                        .then(Commands.literal("deny")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> run(ctx, Action.DENY))))
                        .then(Commands.literal("cancel")
                                .executes(ctx -> {
                                    TradeManager.cancelCommand(ctx.getSource().getPlayerOrException());
                                    return 1;
                                }))
                        .then(Commands.literal("log")
                                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .executes(ctx -> openLog(ctx, null))
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(TradeLog.names(), builder))
                                        .executes(ctx -> openLog(ctx, StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> run(ctx, Action.REQUEST)))));

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TradeManager.server = server;
            TradeLog.load(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> TradeManager.cancelAll());
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                TradeRecovery.restoreOnJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                TradeManager.onLeave(handler.getPlayer(), "reason.left"));
        // Before the death drops: the returned offer lands in the inventory and drops/keeps like everything else
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                TradeManager.onLeave(player, "reason.died");
            }
            return true;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 == 0) {
                TradeManager.expireRequests();
            }
        });
    }

    private enum Action { REQUEST, ACCEPT, DENY }

    private static int run(CommandContext<CommandSourceStack> ctx, Action action) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        ServerPlayer other = EntityArgument.getPlayer(ctx, "player");
        switch (action) {
            case REQUEST -> TradeManager.request(self, other);
            case ACCEPT -> TradeManager.accept(self, other);
            case DENY -> TradeManager.deny(self, other);
        }
        return 1;
    }

    private static int openLog(CommandContext<CommandSourceStack> ctx, String filter) throws CommandSyntaxException {
        ServerPlayer self = ctx.getSource().getPlayerOrException();
        if (!TradeConfig.get().logTrades) {
            TradeManager.fail(self, Lang.get(self, "log.disabled"));
            return 0;
        }
        self.openMenu(new SimpleMenuProvider((id, inventory, player) -> new TradeLogMenu(id, inventory, self, filter),
                TradeLogMenu.title(self, filter)));
        return 1;
    }
}
