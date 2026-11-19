package dev.ftadvisor;

import java.util.*;

/** Статистика цен по предметам: медиана, квартили, отсев выбросов, тренд. */
public final class Stats {
    public static final class Item {
        public String name;
        public List<AuctionData.Lot> lots = new ArrayList<>();
        public int n;
        public double min, p25, median, p75, max;
        public AuctionData.Lot cheapest;
        public double trend = Double.NaN;
        public long trendAgeMin;
    }

    public static Map<String, Item> compute(Collection<AuctionData.Lot> lots) {
        Map<String, Item> m = new LinkedHashMap<>();
        for (AuctionData.Lot l : lots) {
            m.computeIfAbsent(l.name(), k -> { Item i = new Item(); i.name = k; return i; }).lots.add(l);
        }
        for (Item it : m.values()) finish(it);
        return m;
    }

    private static void finish(Item it) {
        List<Double> u = new ArrayList<>();
        for (AuctionData.Lot l : it.lots) u.add(l.unit());
        Collections.sort(u);
        it.n = u.size();
        it.min = u.get(0);
        it.max = u.get(u.size() - 1);
        double raw = pct(u, 0.5);
        List<Double> r = new ArrayList<>();
        for (double x : u) if (x <= raw * 5) r.add(x);
        if (r.isEmpty()) r = u;
        it.median = pct(r, 0.5);
        it.p25 = pct(r, 0.25);
        it.p75 = pct(r, 0.75);
        AuctionData.Lot best = it.lots.get(0);
        for (AuctionData.Lot l : it.lots) if (l.unit() < best.unit()) best = l;
        it.cheapest = best;
        double[] base = History.baseline(it.name, 10 * 60_000L);
        if (base != null && base[1] > 0 && it.n >= 3) {
            it.trend = (it.median - base[1]) / base[1];
            it.trendAgeMin = (System.currentTimeMillis() - (long) base[0]) / 60_000L;
        }
    }

    private static double pct(List<Double> s, double p) {
        int idx = (int) Math.round((s.size() - 1) * p);
        return s.get(Math.max(0, Math.min(s.size() - 1, idx)));
    }
}
