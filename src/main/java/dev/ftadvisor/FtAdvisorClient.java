package dev.ftadvisor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class FtAdvisorClient implements ClientModInitializer {
    private int tick = 0;

    @Override
    public void onInitializeClient() {
        Config.load();
        Catalog.load();

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            tick++;
            if (tick % 5 == 0) AuctionData.scan(mc);
            if (tick % 20 == 0) PlayerInfo.update(mc);
        });

        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) PlayerInfo.onChat(message.getString());
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("fta")
                        .executes(ctx -> {
                            MinecraftClient mc = ctx.getSource().getClient();
                            mc.send(() -> mc.setScreen(new AdvisorScreen()));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("reset").executes(ctx -> {
                            AuctionData.clear();
                            ctx.getSource().sendFeedback(Text.literal("FT Advisor: данные аукциона сброшены."));
                            return 1;
                        }))));
    }
}
