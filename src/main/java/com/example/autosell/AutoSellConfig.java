package com.example.autosell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Luu danh sach vat pham se duoc ban vao config/autosell.json */
public final class AutoSellConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("autosell.json");
    private static final Set<Identifier> SELL = new LinkedHashSet<>();

    private static class Data {
        List<String> sell = new ArrayList<>();
    }

    private AutoSellConfig() {}

    public static void load() {
        SELL.clear();
        if (Files.exists(FILE)) {
            try (Reader r = Files.newBufferedReader(FILE)) {
                Data d = GSON.fromJson(r, Data.class);
                if (d != null && d.sell != null) {
                    for (String s : d.sell) {
                        Identifier id = Identifier.tryParse(s);
                        if (id != null) SELL.add(id);
                    }
                }
                return;
            } catch (Exception e) {
                System.err.println("[AutoSell] Khong doc duoc config, dung mac dinh: " + e);
                SELL.clear();
            }
        }
        // Mac dinh: giu nguyen hanh vi cu (chi ban dua hau)
        SELL.add(Registries.ITEM.getId(Items.MELON_SLICE));
        save();
    }

    public static void save() {
        Data d = new Data();
        for (Identifier id : SELL) d.sell.add(id.toString());
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer w = Files.newBufferedWriter(FILE)) {
                GSON.toJson(d, w);
            }
        } catch (IOException e) {
            System.err.println("[AutoSell] Khong luu duoc config: " + e);
        }
    }

    public static boolean shouldSell(ItemStack stack) {
        return !stack.isEmpty() && shouldSell(stack.getItem());
    }

    public static boolean shouldSell(Item item) {
        return SELL.contains(Registries.ITEM.getId(item));
    }

    public static boolean toggle(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        if (!SELL.remove(id)) {
            SELL.add(id);
            return true;
        }
        return false;
    }

    public static void add(Item item) {
        SELL.add(Registries.ITEM.getId(item));
    }

    public static void clear() {
        SELL.clear();
    }

    public static int count() {
        return SELL.size();
    }
}
