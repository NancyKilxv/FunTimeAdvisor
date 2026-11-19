package dev.ftadvisor;

import java.util.*;

/** Подбирает занятия из каталога под день вайпа, баланс, ранг, цель, риск и время. */
public final class Planner {
    public record Scored(Catalog.Activity a, int score, List<String> why) {}
    private record Gap(String title, long gap, String info) {}
    private record Later(String title, int day) {}

    public static List<String> lastTop = new ArrayList<>();

    private static final int[][] BUDGET = {
            {60, 30, 10}, // деньги: доход, экипировка, запас
            {25, 60, 15}, // экипировка
            {10, 60, 30}, // pvp
            {35, 15, 50}, // база: доход, экипировка, база/запас
            {40, 40, 20}};
    private static final String[][] BUDGET_NAMES = {
            {"доходные вложения (автофарм, флип)", "экипировка", "запас"},
            {"доход", "экипировка и зачарования", "запас"},
            {"доход", "снаряжение и зелья для боя", "запас на замену потерь"},
            {"доход", "экипировка", "база и защита"},
            {"доход", "экипировка", "запас"}};

    static String stage(int day) {
        if (day <= 2) return "Этап 1: старт вайпа";
        if (day <= 7) return "Этап 2: раннее развитие";
        if (day <= 21) return "Этап 3: середина вайпа";
        return "Этап 4: поздний вайп";
    }

    private static int rankTier(String rank) {
        if (rank == null) return 0;
        String r = rank.trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, Integer> e : Config.I.rankTiers.entrySet())
            if (e.getKey().trim().toLowerCase(Locale.ROOT).equals(r)) return e.getValue();
        return 0;
    }

    private static String catName(String c) {
        return switch (c) {
            case "money" -> "Деньги";
            case "gear" -> "Экипировка";
            case "pvp" -> "PvP";
            case "base" -> "База";
            default -> "Прочее";
        };
    }

    private static Scored score(Catalog.Activity a, AuctionData.Lot lot, String info, int day, int timeBudget, int goal) {
        Config c = Config.I;
        String goalCat = Config.GOAL_CAT[goal];
        int s = a.priority * 6;
        List<String> why = new ArrayList<>();
        if (goalCat.isEmpty()) {
            s += 10;
        } else if (a.cat.equals(goalCat)) {
            s += 25;
            why.add("совпадает с целью «" + Config.GOALS[goal] + "»");
        }
        int span = Math.min(a.maxDay, 60) - a.minDay;
        if (span > 0 && day - a.minDay <= span / 3) { s += 5; why.add("лучшее время для этого - сейчас"); }
        if (a.risk <= c.risk) {
            s += 10;
            why.add("риск в твоих пределах");
        } else {
            s -= (a.risk - c.risk) * 18;
            why.add("рискованнее выбранного уровня");
        }
        if (a.minutes <= timeBudget) {
            s += 8;
            why.add("влезает в сессию (~" + a.minutes + " мин)");
        } else {
            s -= 12;
            why.add("долго для сессии (~" + a.minutes + " мин)");
        }
        if (lot != null) { s += 6; why.add(info + ", хватает"); }
        return new Scored(a, s, why);
    }

    public static List<String> plan(long balance, String rank, int day) {
        Config c = Config.I;
        int goal = Config.clamp(c.goal, Config.GOALS.length);
        int timeBudget = Config.TIMES_MIN[Config.clamp(c.sessionTime, Config.TIMES_MIN.length)];
        int tier = rankTier(rank);

        List<Scored> ok = new ArrayList<>();
        List<Gap> saveUp = new ArrayList<>();
        List<Later> later = new ArrayList<>();

        for (Catalog.Activity a : Catalog.all()) {
            if (day > a.maxDay) continue;
            if (day < a.minDay) { later.add(new Later(a.title, a.minDay)); continue; }
            if (tier < a.minRank) continue;
            if (balance >= 0 && balance > a.maxBal) continue;
            AuctionData.Lot lot = (a.watch == null || a.watch.isEmpty()) ? null : Advisor.cheapestFor(a.watch);
            long need = a.minBal;
            String info = "";
            if (lot != null) { need = Math.max(need, lot.price()); info = "на аукционе от " + Advisor.money(lot.price()); }
            if (balance >= 0 && balance < need) { saveUp.add(new Gap(a.title, need - balance, info)); continue; }
            ok.add(score(a, lot, info, day, timeBudget, goal));
        }

        // динамическое занятие по данным аукциона
        List<Advisor.Deal> deals = Advisor.deals(Stats.compute(AuctionData.all()));
        List<Advisor.Deal> affordable = new ArrayList<>();
        for (Advisor.Deal d : deals) if (balance < 0 || d.lot().price() <= balance) affordable.add(d);
        if (!affordable.isEmpty()) {
            Catalog.Activity f = new Catalog.Activity();
            f.id = "flip_live"; f.title = "Выгодные лоты сейчас (по данным аукциона)"; f.cat = "money";
            f.risk = 1; f.minutes = 15; f.priority = 8; f.income = "зависит от лота";
            for (Advisor.Deal d : affordable.subList(0, Math.min(3, affordable.size())))
                f.steps.add("Купи " + d.lot().name() + " x" + d.lot().count() + " за " + Advisor.money(d.lot().price())
                        + " (скидка " + Math.round(d.discount() * 100) + "%, профит ~" + Advisor.money(d.profit()) + ").");
            f.steps.add("Перевыстави около медианы, чтобы получить прибыль.");
            ok.add(score(f, null, "", day, timeBudget, goal));
        }

        ok.sort((x, y) -> Integer.compare(y.score(), x.score()));
        saveUp.sort(Comparator.comparingLong(Gap::gap));
        later.sort(Comparator.comparingInt(Later::day));

        List<String> o = new ArrayList<>();
        o.add("##" + stage(day));
        o.add("Ранг: " + (rank == null || rank.isEmpty() ? "не определён" : rank)
                + " | Баланс: " + (balance < 0 ? "неизвестен" : Advisor.money(balance)) + " | День вайпа: " + day);
        o.add("Профиль: цель «" + Config.GOALS[goal] + "», риск «" + Config.RISKS[Config.clamp(c.risk, 3)]
                + "», сессия " + Config.TIMES[Config.clamp(c.sessionTime, 4)]);
        if (AuctionData.size() == 0) o.add("Данных аукциона нет: советы по покупкам будут менее точными.");
        o.add("");

        lastTop = new ArrayList<>();
        o.add("##Что делать сейчас (по убыванию пользы)");
        int n = Math.min(c.planSize, ok.size());
        if (n == 0) o.add("Подходящих занятий не нашлось. Попробуй поменять цель, риск или день вайпа.");
        for (int i = 0; i < n; i++) {
            Scored s = ok.get(i);
            Catalog.Activity a = s.a();
            lastTop.add(a.title);
            o.add("");
            o.add("##" + (i + 1) + ". " + a.title + "  (оценка " + s.score() + ")" + (a.verify ? "  [проверить на сервере]" : ""));
            o.add(catName(a.cat) + " | риск: " + Config.RISKS[Config.clamp(a.risk, 3)].toLowerCase(Locale.ROOT)
                    + " | ~" + a.minutes + " мин" + (a.income == null || a.income.isEmpty() || a.income.equals("-") ? "" : " | доход: " + a.income));
            o.add("Почему: " + String.join("; ", s.why()));
            if (i < c.planDetailed) for (String st : a.steps) o.add("  - " + st);
        }

        if (!saveUp.isEmpty()) {
            o.add("");
            o.add("##Копи на это");
            for (Gap g : saveUp.subList(0, Math.min(8, saveUp.size())))
                o.add("• " + g.title() + ": нужно ещё " + Advisor.money(g.gap()) + (g.info().isEmpty() ? "" : " (" + g.info() + ")"));
        }
        if (!later.isEmpty()) {
            o.add("");
            o.add("##Станет актуально позже");
            for (Later l : later.subList(0, Math.min(6, later.size())))
                o.add("• " + l.title() + " (с " + l.day() + "-го дня)");
        }

        o.add("");
        o.add("##Как распределить баланс (ориентир)");
        int[] b = BUDGET[goal];
        for (int i = 0; i < 3; i++)
            o.add("• " + b[i] + "% - " + BUDGET_NAMES[goal][i] + (balance >= 0 ? ": " + Advisor.money(balance * b[i] / 100) : ""));
        o.add("");
        o.add("Каталог занятий можно править: config/ft_advisor_activities.json");
        return o;
    }
}
