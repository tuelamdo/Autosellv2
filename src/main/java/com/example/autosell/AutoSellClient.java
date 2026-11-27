package com.example.autosell;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

public class AutoSellClient implements ClientModInitializer {

    private enum State { IDLE, WAIT_OPEN, MOVING, WAIT_CLOSE }

    private static KeyBinding toggleKey;
    private static KeyBinding menuKey;
    private static boolean enabled = false;
    private static State state = State.IDLE;
    private static int timer = 0;      // dem tick cho timeout / delay
    private static int cooldown = 0;   // nghi giua cac lan sell

    @Override
    public void onInitializeClient() {
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosell.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "category.autosell"
        ));

        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.autosell.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_L,
                "category.autosell"
        ));

        AutoSellConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register(AutoSellClient::onTick);
    }

    private static void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) {
            state = State.IDLE;
            return;
        }

        while (menuKey.wasPressed()) {
            if (mc.currentScreen == null) {
                mc.setScreen(new AutoSellScreen());
            }
        }

        while (toggleKey.wasPressed()) {
            enabled = !enabled;
            state = State.IDLE;
            cooldown = 0;
            sendStatus(mc, enabled);
        }

        if (!enabled) return;
        if (cooldown > 0) cooldown--;

        switch (state) {
            case IDLE -> {
                // Chi gui /sell khi khong mo GUI nao, co vat pham can ban, het cooldown
                if (mc.currentScreen == null && cooldown == 0 && hasSellable(mc)) {
                    mc.player.networkHandler.sendChatCommand("sell");
                    state = State.WAIT_OPEN;
                    timer = 0;
                }
            }
            case WAIT_OPEN -> {
                timer++;
                if (mc.currentScreen instanceof GenericContainerScreen) {
                    state = State.MOVING;
                    timer = 0;
                } else if (timer > 60) { // 3 giay khong mo duoc ruong -> thu lai sau
                    state = State.IDLE;
                    cooldown = 40;
                }
            }
            case MOVING -> {
                if (!(mc.currentScreen instanceof GenericContainerScreen screen)
                        || !(screen.getScreenHandler() instanceof GenericContainerScreenHandler handler)) {
                    state = State.IDLE;
                    cooldown = 20;
                    return;
                }
                int chestSize = handler.getRows() * 9;
                int clicks = 0;
                boolean chestFull = false;

                for (int i = chestSize; i < handler.slots.size() && clicks < 4; i++) {
                    ItemStack stack = handler.getSlot(i).getStack();
                    if (AutoSellConfig.shouldSell(stack)) {
                        Item item = stack.getItem();
                        int before = stack.getCount();
                        mc.interactionManager.clickSlot(handler.syncId, i, 0,
                                SlotActionType.QUICK_MOVE, mc.player);
                        clicks++;
                        ItemStack after = handler.getSlot(i).getStack();
                        if (after.isOf(item) && after.getCount() == before) {
                            chestFull = true; // khong chuyen duoc nua
                            break;
                        }
                    }
                }

                if (clicks == 0 || chestFull) {
                    state = State.WAIT_CLOSE;
                    timer = 0;
                }
            }
            case WAIT_CLOSE -> {
                timer++;
                if (timer >= 4) { // doi 4 tick cho server dong bo roi dong ruong -> server sell
                    mc.player.closeHandledScreen();
                    state = State.IDLE;
                    cooldown = 20; // 1 giay truoc khi sell lan tiep theo
                }
            }
        }
    }

    private static boolean hasSellable(MinecraftClient mc) {
        for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
            if (AutoSellConfig.shouldSell(mc.player.getInventory().getStack(i))) return true;
        }
        return false;
    }

    // Chi hien thi cho nguoi choi (client-side), khong gui len server
    private static void sendStatus(MinecraftClient mc, boolean on) {
        Text msg = Text.literal("[AutoSell] ").formatted(Formatting.GOLD)
                .append(Text.literal(on ? "ĐÃ BẬT" : "ĐÃ TẮT")
                        .formatted(on ? Formatting.GREEN : Formatting.RED));
        mc.player.sendMessage(msg, false);
    }
}
