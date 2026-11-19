package dev.ftadvisor;

import java.util.*;

/** Аналитика аукциона: сводка, сделки, поиск. */
public final class Advisor {
    public static String money(long v) { return "$" + String.format(Locale.US, "%,d", v); }
    public static String moneyD(double v) { return money(Math.round(v)); }
    private static String pct(double d) { return String.format(Locale.US, "%+.0f%%", d * 100); }

    private static String age(long min) {
        if (min < 90) return min + " мин";
        return (min / 60) + " ч";
    }

    public static String trendText(Stats.Item it) {
        if (Double.isNaN(it.trend)) return "истории пока нет";
        String w = Math.abs(it.trend) < 0.03 ? "стабильно" : (it.trend > 0 ? "дорожает" : "дешевеет");
        return w + " " + pct(it.trend) + " за " + age(it.trendAgeMin);
    }

    public record Deal(AuctionData.Lot lot, Stats.Item item, double discount, long profit) {}

    public static List<Deal> deals(Map<String, Stats.Item> st) {
        List<Deal> out = new ArrayList<>();
        Config c = Config.I;
        for (Stats.Item it : st.values()) {
            if (it.n < c.minListings) continue;
            double net = it.median * (1 - c.feePercent / 100.0);
            for (AuctionData.Lot l : it.lots) {
                double u = l.unit();
                if (u <= it.median * c.dealThreshold) {
                    long profit = Math.round((net - u) * l.count());
                    if (profit > 0) out.add(new Deal(l, it, 1 - u / it.median, profit));
                }
            }
        }
        out.sort((a, b) -> Long.compare(b.profit(), a.profit()));
        return out;
    }

    /** Самый дешёвый лот по ключевому слову. */
    public static AuctionData.Lot cheapestFor(String kw) {
        String k = kw.toLowerCase(Locale.ROOT);
        AuctionData.Lot best = null;
        for (AuctionData.Lot l : AuctionData.all()) {
            if (l.name().toLowerCase(Locale.ROOT).contains(k) && (best == null || l.unit() < best.unit())) best = l;
        }
        return best;
    }

    public static List<String> summary(long balance) {
        List<String> o = new ArrayList<>();
        Collection<AuctionData.Lot> lots = AuctionData.all();
        if (lots.isEmpty()) {
            o.add("##Нет данных");
            o.add("Открой /ah и пролистай несколько страниц стрелкой вправо, потом вернись в меню (/fta). Чем больше страниц, тем точнее анализ.");
            return o;
        }
        AuctionData.analyzed = true;
        Map<String, Stats.Item> st = Stats.compute(lots);
        History.record(st.values());

        o.add("##Итог по аукциону");
        o.add("Лотов: " + lots.size() + " | предметов: " + st.size() + " | страниц просмотрено: " + AuctionData.pagesSeen);
        o.add("Самым старым данным " + age(AuctionData.oldestAgeMin()) + " (старше " + Config.I.lotMaxAgeMin + " мин удаляются).");
        if (AuctionData.pagesSeen < 5) o.add("Страниц мало: цены могут быть неточными. Пролистай ещё.");
        if (balance >= 0) {
            o.add("Баланс " + money(balance) + ":");
            for (int p : new int[]{25, 50, 100}) {
                long lim = balance * p / 100;
                long cnt = lots.stream().filter(l -> l.price() <= lim).count();
                o.add("  • на " + p + "% баланса (" + money(lim) + ") доступно " + cnt + " лотов");
            }
        } else {
            o.add("Баланс не определён: введи его вручную.");
        }

        o.add("");
        o.add("##Ключевые предметы");
        Set<String> shown = new HashSet<>();
        for (String kw : Config.I.watchKeywords) {
            AuctionData.Lot l = cheapestFor(kw);
            if (l == null || !shown.add(l.name())) continue;
            Stats.Item it = st.get(l.name());
            String ok = balance < 0 ? "" : (l.price() <= balance ? " [хватает]" : " [не хватает " + money(l.price() - balance) + "]");
            o.add("• " + l.name() + ": от " + money(l.price()) + (l.count() > 1 ? " (" + l.count() + " шт.)" : "") + ok);
            if (it != null && it.n >= Config.I.minListings)
                o.add("    лотов " + it.n + ", медиана " + moneyD(it.median) + "/шт., диапазон " + moneyD(it.p25) + " - " + moneyD(it.p75) + ", " + trendText(it));
            else if (it != null)
                o.add("    лотов " + it.n + ": мало для надёжной медианы");
        }
        if (shown.isEmpty()) o.add("Ключевые предметы не найдены на просмотренных страницах.");

        o.add("");
        o.add("##Самые ходовые предметы (по числу лотов)");
        st.values().stream().sorted((a, b) -> Integer.compare(b.n, a.n)).limit(8).forEach(it ->
                o.add("• " + it.name + ": " + it.n + " лотов, медиана " + moneyD(it.median) + "/шт., " + trendText(it)));

        List<Stats.Item> moving = new ArrayList<>();
        for (Stats.Item it : st.values()) if (!Double.isNaN(it.trend) && Math.abs(it.trend) >= 0.10) moving.add(it);
        if (!moving.isEmpty()) {
            o.add("");
            o.add("##Заметные изменения цен");
            moving.sort((a, b) -> Double.compare(Math.abs(b.trend), Math.abs(a.trend)));
            for (Stats.Item it : moving.subList(0, Math.min(8, moving.size())))
                o.add("• " + it.name + ": " + trendText(it) + " (сейчас " + moneyD(it.median) + "/шт.)");
        }

        o.add("");
        o.add("Дальше: вкладка «Сделки», затем «Анализ ситуации».");
        return o;
    }

    public static List<String> dealsText(long balance) {
        List<String> o = new ArrayList<>();
        Collection<AuctionData.Lot> lots = AuctionData.all();
        if (lots.isEmpty()) { o.add("Нет данных. Сначала пролистай /ah."); return o; }
        Map<String, Stats.Item> st = Stats.compute(lots);
        List<Deal> deals = deals(st);
        int pctT = (int) Math.round(Config.I.dealThreshold * 100);
        o.add("##Выгодные лоты (цена за штуку ниже " + pctT + "% медианы)");
        o.add("Оценка по текущим лотам. Мало покупателей или комиссия могут уменьшить реальную прибыль.");
        if (deals.isEmpty()) o.add("Подходящих лотов нет. Пролистай больше страниц или позже обнови данные.");
        int shown = 0;
        for (Deal d : deals) {
            if (shown++ >= 15) break;
            AuctionData.Lot l = d.lot();
            String ok = balance < 0 ? "" : (l.price() <= balance ? " [хватает]" : " [не хватает " + money(l.price() - balance) + "]");
            o.add("• " + l.name() + " x" + l.count() + ": " + money(l.price()) + ok);
            o.add("    медиана " + moneyD(d.item().median) + "/шт., скидка " + Math.round(d.discount() * 100) + "%, возможный профит ~" + money(d.profit()) + ", лотов " + d.item().n);
        }

        List<String> over = new ArrayList<>();
        List<AuctionData.Lot> overLots = new ArrayList<>();
        Map<AuctionData.Lot, Stats.Item> owner = new HashMap<>();
        for (Stats.Item it : st.values()) {
            if (it.n < Config.I.minListings) continue;
            for (AuctionData.Lot l : it.lots)
                if (l.unit() >= it.median * 1.6 && l.unit() <= it.median * 5) { overLots.add(l); owner.put(l, it); }
        }
        overLots.sort((a, b) -> Double.compare(b.unit() / owner.get(b).median, a.unit() / owner.get(a).median));
        for (AuctionData.Lot l : overLots.subList(0, Math.min(6, overLots.size())))
            over.add("• " + l.name() + " x" + l.count() + ": " + money(l.price()) + " (дороже медианы на " + Math.round((l.unit() / owner.get(l).median - 1) * 100) + "%)");
        if (!over.isEmpty()) {
            o.add("");
            o.add("##Переоценено, не покупай");
            o.addAll(over);
        }
        return o;
    }

    public static List<String> search(String q, long balance) {
        List<String> o = new ArrayList<>();
        String k = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (k.isEmpty()) {
            o.add("##Поиск предмета");
            o.add("Введи часть названия в поле «Поиск предмета» (например: элитр, ключ, пузырёк).");
            return o;
        }
        Map<String, Stats.Item> st = Stats.compute(AuctionData.all());
        List<Stats.Item> hits = new ArrayList<>();
        for (Stats.Item it : st.values()) if (it.name.toLowerCase(Locale.ROOT).contains(k)) hits.add(it);
        if (hits.isEmpty()) {
            o.add("##Ничего не найдено");
            o.add("Среди просмотренных лотов нет «" + q + "». Пролистай больше страниц /ah.");
            return o;
        }
        hits.sort((a, b) -> Integer.compare(b.n, a.n));
        o.add("##Найдено предметов: " + hits.size());
        for (Stats.Item it : hits.subList(0, Math.min(6, hits.size()))) {
            o.add("");
            o.add("##" + it.name);
            o.add("Лотов: " + it.n + " | минимум " + moneyD(it.min) + "/шт. | медиана " + moneyD(it.median) + "/шт. | максимум " + moneyD(it.max) + "/шт.");
            o.add("Типичный диапазон: " + moneyD(it.p25) + " - " + moneyD(it.p75) + " | " + trendText(it));
            if (it.n < Config.I.minListings) o.add("Мало лотов: медиана ненадёжна.");
            List<AuctionData.Lot> ls = new ArrayList<>(it.lots);
            ls.sort(Comparator.comparingDouble(AuctionData.Lot::unit));
            for (AuctionData.Lot l : ls.subList(0, Math.min(3, ls.size()))) {
                String ok = balance < 0 ? "" : (l.price() <= balance ? " [хватает]" : " [не хватает " + money(l.price() - balance) + "]");
                o.add("  • " + money(l.price()) + " за " + l.count() + " шт. (" + moneyD(l.unit()) + "/шт.), продавец " + l.seller() + ok);
            }
            o.add("  Совет: чтобы продать быстро, ставь около " + moneyD(it.p25 > 0 ? it.p25 : it.median) + "/шт.; по рынку - около " + moneyD(it.median) + "/шт.");
        }
        return o;
    }

    public static String aiPrompt(long balance, String rank, int day) {
        Planner.plan(balance, rank, day);
        StringBuilder sb = new StringBuilder();
        sb.append("Ты помощник игрока на анархии-сервере Minecraft (FunTime). Составь короткий пошаговый план развития на русском (6-8 пунктов).\n");
        sb.append("Ранг: ").append(rank).append(", баланс: ").append(balance < 0 ? "неизвестен" : money(balance)).append(", день вайпа: ").append(day).append(".\n");
        sb.append("Цель: ").append(Config.GOALS[Config.clamp(Config.I.goal, Config.GOALS.length)])
          .append(", риск: ").append(Config.RISKS[Config.clamp(Config.I.risk, 3)])
          .append(", время сессии: ").append(Config.TIMES[Config.clamp(Config.I.sessionTime, 4)]).append(".\n");
        sb.append("Занятия, которые подобрал мод: ").append(String.join("; ", Planner.lastTop)).append(".\n");
        sb.append("Данные аукциона (самые дешёвые лоты ключевых предметов):\n");
        Set<String> seen = new HashSet<>();
        for (String kw : Config.I.watchKeywords) {
            AuctionData.Lot l = cheapestFor(kw);
            if (l != null && seen.add(l.name())) sb.append("- ").append(l.name()).append(": ").append(money(l.price())).append(" за ").append(l.count()).append(" шт.\n");
        }
        sb.append("Не выдумывай факты о сервере, опирайся на эти данные.");
        return sb.toString();
    }
}
