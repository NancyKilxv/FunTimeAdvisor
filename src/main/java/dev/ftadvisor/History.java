package dev.ftadvisor;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** История медианных цен между сеансами, чтобы видеть рост и падение. */
public final class History {
    private static final Gson GSON = new Gson();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("ft_advisor_history.json");
    private static Map<String, List<double[]>> data = new HashMap<>();
    private static boolean loaded = false;

    private static void ensure() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(PATH)) {
                Map<String, List<double[]>> m = GSON.fromJson(Files.readString(PATH),
                        new TypeToken<Map<String, List<double[]>>>() {}.getType());
                if (m != null) data = m;
            }
        } catch (Exception e) {
            data = new HashMap<>();
        }
    }

    public static void record(Collection<Stats.Item> items) {
        ensure();
        long now = System.currentTimeMillis();
        long weekAgo = now - 7L * 24 * 3600 * 1000;
        for (Stats.Item it : items) {
            if (it.n < 3) continue;
            List<double[]> list = data.computeIfAbsent(it.name, k -> new ArrayList<>());
            if (!list.isEmpty() && now - list.get(list.size() - 1)[0] < 5 * 60_000L)
                list.set(list.size() - 1, new double[]{now, it.median});
            else
                list.add(new double[]{now, it.median});
            while (list.size() > 12) list.remove(0);
        }
        data.values().removeIf(l -> l.isEmpty() || l.get(l.size() - 1)[0] < weekAgo);
        try {
            Files.writeString(PATH, GSON.toJson(data));
        } catch (Exception ignored) {
        }
    }

    /** Самая свежая запись не младше minAgeMs и не старше 48 часов. */
    public static double[] baseline(String name, long minAgeMs) {
        ensure();
        List<double[]> list = data.get(name);
        if (list == null) return null;
        long now = System.currentTimeMillis();
        for (int i = list.size() - 1; i >= 0; i--) {
            long age = now - (long) list.get(i)[0];
            if (age >= minAgeMs && age <= 48L * 3600 * 1000) return list.get(i);
        }
        return null;
    }
}
