package dev.ftadvisor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {
    public static final String[] GOALS = {"Деньги", "Экипировка", "PvP", "База и защита", "Универсально"};
    public static final String[] GOAL_CAT = {"money", "gear", "pvp", "base", ""};
    public static final String[] RISKS = {"Низкий", "Средний", "Высокий"};
    public static final String[] TIMES = {"до 30 мин", "1-2 часа", "2-4 часа", "5+ часов"};
    public static final int[] TIMES_MIN = {30, 90, 180, 360};

    public int version = 2;
    public String apiKey = "";
    public String apiUrl = "https://api.deepseek.com/chat/completions";
    public String apiModel = "deepseek-chat";

    public int wipeDay = 1;
    public int goal = 4;
    public int risk = 1;
    public int sessionTime = 1;

    /** Минимум лотов одного предмета, чтобы доверять медиане. */
    public int minListings = 3;
    /** Лот считается выгодным, если цена за штуку <= медиана * это число. */
    public double dealThreshold = 0.75;
    /** Комиссия аукциона в процентах (если знаешь, впиши; по умолчанию 0). */
    public double feePercent = 0;
    /** Данные о лотах старше этого срока (минуты) считаются устаревшими и удаляются. */
    public int lotMaxAgeMin = 90;
    public int planSize = 12;
    public int planDetailed = 4;

    /** Ключевые слова предметов, цены которых мод отслеживает отдельно. */
    public List<String> watchKeywords = new ArrayList<>(List.of(
            "автошахт", "спавнер", "ключ", "маяк", "элитр", "незерит", "шалкер", "пузырёк опыта", "зель",
            "сфера", "талисман", "икар", "трапк", "пласт", "динамит", "изумруд", "вампиризм", "радиац", "яйц"));

    /** Уровни рангов для фильтра занятий (чем выше число, тем круче ранг). Впиши свои. */
    public Map<String, Integer> rankTiers = new HashMap<>(Map.of("Игрок", 0));

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("ft_advisor.json");
    public static Config I = new Config();

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                Config c = GSON.fromJson(Files.readString(PATH), Config.class);
                if (c != null) {
                    if (c.version < 2) { c.watchKeywords = new Config().watchKeywords; c.version = 2; }
                    I = c;
                }
            }
            save();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void save() {
        try {
            Files.writeString(PATH, GSON.toJson(I));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int clamp(int v, int len) { return Math.max(0, Math.min(len - 1, v)); }
}
