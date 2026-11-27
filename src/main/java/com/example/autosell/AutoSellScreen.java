package com.example.autosell;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Menu chon vat pham de ban. Mo bang phim L. */
public class AutoSellScreen extends Screen {

    private record Entry(Item item, ItemStack stack, Identifier id, String key) {}

    private static final int CELL = 20;
    private static final int GRID_TOP = 44;

    private final List<Entry> all = new ArrayList<>();
    private final List<Entry> filtered = new ArrayList<>();

    private TextFieldWidget searchField;
    private ButtonWidget onlySelectedButton;
    private String query = "";
    private boolean onlySelected = false;
    private int scroll = 0; // hang dau tien dang hien
    private int cols = 12;

    public AutoSellScreen() {
        super(Text.literal("AutoSell - Chọn vật phẩm để bán"));
        for (Item item : Registries.ITEM) {
            if (item == net.minecraft.item.Items.AIR) continue;
            ItemStack stack = item.getDefaultStack();
            Identifier id = Registries.ITEM.getId(item);
            String key = (stack.getName().getString() + " " + id.getPath()).toLowerCase(Locale.ROOT);
            all.add(new Entry(item, stack, id, key));
        }
    }

    @Override
    protected void init() {
        cols = Math.max(6, Math.min(16, (width - 40) / CELL));

        searchField = new TextFieldWidget(textRenderer, width / 2 - 100, 22, 200, 16, Text.literal("Tìm"));
        searchField.setPlaceholder(Text.literal("Tìm vật phẩm..."));
        searchField.setText(query);
        searchField.setChangedListener(s -> {
            query = s;
            scroll = 0;
            refilter();
        });
        addDrawableChild(searchField);
        setInitialFocus(searchField);

        int y1 = height - 44;
        int y2 = height - 22;
        int left = width / 2 - 129;
        int right = width / 2 + 4;

        onlySelectedButton = ButtonWidget.builder(onlySelectedLabel(), b -> {
            onlySelected = !onlySelected;
            b.setMessage(onlySelectedLabel());
            scroll = 0;
            refilter();
        }).dimensions(left, y1, 125, 18).build();
        addDrawableChild(onlySelectedButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("Bỏ chọn tất cả"), b -> {
            AutoSellConfig.clear();
            refilter();
        }).dimensions(right, y1, 125, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Chọn đồ trong túi"), b -> {
            if (client != null && client.player != null) {
                for (int i = 0; i < PlayerInventory.MAIN_SIZE; i++) {
                    ItemStack s = client.player.getInventory().getStack(i);
                    if (!s.isEmpty()) AutoSellConfig.add(s.getItem());
                }
            }
            refilter();
        }).dimensions(left, y2, 125, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Xong"), b -> close())
                .dimensions(right, y2, 125, 18).build());

        refilter();
    }

    private Text onlySelectedLabel() {
        return Text.literal("Chỉ hiện đã chọn: " + (onlySelected ? "BẬT" : "TẮT"));
    }

    private void refilter() {
        filtered.clear();
        String q = query.trim().toLowerCase(Locale.ROOT);
        for (Entry e : all) {
            if (onlySelected && !AutoSellConfig.shouldSell(e.item())) continue;
            if (!q.isEmpty() && !e.key().contains(q)) continue;
            filtered.add(e);
        }
        clampScroll();
    }

    private int gridLeft() {
        return (width - cols * CELL) / 2;
    }

    private int visibleRows() {
        int bottom = height - 64;
        return Math.max(1, (bottom - GRID_TOP) / CELL);
    }

    private int totalRows() {
        return (filtered.size() + cols - 1) / cols;
    }

    private void clampScroll() {
        int max = Math.max(0, totalRows() - visibleRows());
        scroll = Math.max(0, Math.min(scroll, max));
    }

    private int indexAt(double mx, double my) {
        int gx = gridLeft();
        if (mx < gx || my < GRID_TOP || mx >= gx + cols * CELL || my >= GRID_TOP + visibleRows() * CELL) {
            return -1;
        }
        int col = (int) ((mx - gx) / CELL);
        int row = (int) ((my - GRID_TOP) / CELL);
        int idx = (scroll + row) * cols + col;
        return idx < filtered.size() ? idx : -1;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta); // nen + cac widget

        ctx.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, 0xFFFFFFFF);

        int gx = gridLeft();
        int rows = visibleRows();
        int hovered = indexAt(mouseX, mouseY);

        // Nen luoi
        ctx.fill(gx - 2, GRID_TOP - 2, gx + cols * CELL + 2, GRID_TOP + rows * CELL + 2, 0xAA000000);

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (scroll + r) * cols + c;
                if (idx >= filtered.size()) break;
                Entry e = filtered.get(idx);
                int x = gx + c * CELL;
                int y = GRID_TOP + r * CELL;

                if (AutoSellConfig.shouldSell(e.item())) {
                    ctx.fill(x, y, x + CELL, y + CELL, 0xAA33CC33);
                    ctx.drawBorder(x, y, CELL, CELL, 0xFF66FF66);
                }
                if (idx == hovered) {
                    ctx.fill(x, y, x + CELL, y + CELL, 0x66FFFFFF);
                }
                ctx.drawItem(e.stack(), x + 2, y + 2);
            }
        }

        // Thanh cuon
        int totalRows = totalRows();
        if (totalRows > rows) {
            int barX = gx + cols * CELL + 4;
            int barH = rows * CELL;
            int thumbH = Math.max(10, barH * rows / totalRows);
            int maxScroll = totalRows - rows;
            int thumbY = GRID_TOP + (barH - thumbH) * scroll / maxScroll;
            ctx.fill(barX, GRID_TOP, barX + 3, GRID_TOP + barH, 0x66000000);
            ctx.fill(barX, thumbY, barX + 3, thumbY + thumbH, 0xFFCCCCCC);
        }

        // Dong thong tin
        String info = "Đã chọn bán: " + AutoSellConfig.count() + "   |   Đang hiện: " + filtered.size();
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(info), width / 2, height - 58, 0xFFAAAAAA);

        if (hovered >= 0) {
            Entry e = filtered.get(hovered);
            ctx.drawTooltip(textRenderer, e.stack().getName(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            int idx = indexAt(mouseX, mouseY);
            if (idx >= 0) {
                AutoSellConfig.toggle(filtered.get(idx).item());
                if (onlySelected) { // bo chon thi bien mat khoi danh sach
                    refilter();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0) {
            scroll -= (int) Math.signum(verticalAmount);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void removed() {
        AutoSellConfig.save();
        super.removed();
    }
}
