package dev.ftadvisor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Копит лоты аукциона, которые игрок сам открыл. Ничего не кликает. */
public final class AuctionData {
    public record Lot(String name, int count, long price, String seller, long seen) {
        public double unit() { return (double) price / Math.max(1, count); }
        String key() { return name + "|" + seller + "|" + price + "|" + count; }
    }

    private static final Pattern PRICE = Pattern.compile("Цена\\s*:\\s*\\$?\\s*([0-9][0-9.,\\s]*)");
    private static final Pattern SELLER = Pattern.compile("Продавец\\s*:\\s*(\\S+)");

    private static final Map<String, Lot> LOTS = new LinkedHashMap<>();
    private static String lastSig = "";
    public static int pagesSeen = 0;
    public static boolean analyzed = false;

    private static void purge() {
        long cutoff = System.currentTimeMillis() - Config.I.lotMaxAgeMin * 60_000L;
        LOTS.values().removeIf(l -> l.seen() < cutoff);
    }

    public static Collection<Lot> all() { purge(); return new ArrayList<>(LOTS.values()); }
    public static int size() { purge(); return LOTS.size(); }

    public static long oldestAgeMin() {
        long min = Long.MAX_VALUE;
        for (Lot l : LOTS.values()) min = Math.min(min, l.seen());
        return min == Long.MAX_VALUE ? 0 : (System.currentTimeMillis() - min) / 60_000L;
    }

    public static void clear() {
        LOTS.clear();
        lastSig = "";
        pagesSeen = 0;
        analyzed = false;
    }

    public static void scan(MinecraftClient mc) {
        if (!(mc.currentScreen instanceof GenericContainerScreen screen)) return;
        GenericContainerScreenHandler h = screen.getScreenHandler();
        int total = h.getRows() * 9;
        StringBuilder sig = new StringBuilder();
        List<Lot> found = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < total && i < h.slots.size(); i++) {
            Slot slot = h.slots.get(i);
            ItemStack st = slot.getStack();
            if (st.isEmpty()) continue;
            LoreComponent lore = st.get(DataComponentTypes.LORE);
            if (lore == null) continue;
            long price = -1;
            String seller = "?";
            for (Text line : lore.lines()) {
                String s = line.getString();
                Matcher m = PRICE.matcher(s);
                if (m.find()) {
                    String digits = m.group(1).replaceAll("[^0-9]", "");
                    if (!digits.isEmpty()) price = Long.parseLong(digits);
                }
                Matcher ms = SELLER.matcher(s);
                if (ms.find()) seller = ms.group(1);
            }
            if (price < 0) continue;
            String name = st.getName().getString().replaceAll("^\\[[^\\]]*\\]\\s*", "").trim();
            Lot lot = new Lot(name, st.getCount(), price, seller, now);
            found.add(lot);
            sig.append(lot.key()).append(';');
        }
        if (found.isEmpty()) return;
        for (Lot l : found) LOTS.put(l.key(), l);
        String s = sig.toString();
        if (!s.equals(lastSig)) {
            lastSig = s;
            pagesSeen++;
        }
    }
}
