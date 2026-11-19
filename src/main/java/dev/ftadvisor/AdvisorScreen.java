package dev.ftadvisor;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class AdvisorScreen extends Screen {
    enum Tab {
        SUMMARY("Сводка"), DEALS("Сделки"), PLAN("План"), SEARCH("Поиск"), AI("ИИ"), HELP("Справка");
        final String label;
        Tab(String l) { label = l; }
    }

    private static final Map<Tab, List<String>> CONTENT = new EnumMap<>(Tab.class);
    private static Tab current = Tab.HELP;

    static {
        CONTENT.put(Tab.HELP, help());
    }

    private record Row(OrderedText text, int color) {}

    private final List<Row> rows = new ArrayList<>();
    private int scroll = 0;
    private int W, H, px, py;
    private int ax, ay, aw, ah;
    private int[] fx = new int[4], fw = new int[4];
    private TextFieldWidget dayField, balField, searchField, keyField;
    private RoundedButton btnAuction, btnSituation, btnAi, btnGoal, btnRisk, btnTime;
    private final List<RoundedButton> tabButtons = new ArrayList<>();
    private final List<ClickableWidget> drawOrder = new ArrayList<>();

    public AdvisorScreen() {
        super(Text.literal("FT Advisor"));
    }

    private static List<String> help() {
        List<String> l = new ArrayList<>();
        l.add("##Как пользоваться");
        l.add("1) Открой /ah и пролистай страниц 5-10 (чем больше, тем точнее).");
        l.add("2) Закрой аукцион и открой это меню командой /fta.");
        l.add("3) Выбери цель, риск и время сессии, введи день вайпа.");
        l.add("4) «Анализ аукциона» даёт сводку, сделки и поиск по предметам.");
        l.add("5) После него разблокируется «Анализ ситуации»: персональный план.");
        l.add("");
        l.add("##Что умеет мод");
        l.add("• Читает цены лотов, пока ты листаешь /ah. Сам ничего не нажимает.");
        l.add("• Считает цену за штуку, медиану, диапазон и тренд по каждому предмету.");
        l.add("• Находит лоты ниже рынка и оценивает возможный профит.");
        l.add("• Подбирает занятия из каталога из ~62 вариантов, включая механики FunTime под твой профиль.");
        l.add("• Показывает, на что копить и что станет актуально позже.");
        l.add("• Поиск по названию предмета с советом по цене продажи.");
        l.add("• «ИИ»: дополнительный совет через API (по желанию).");
        l.add("");
        l.add("##Настройка");
        l.add("config/ft_advisor.json: пороги, ключевые слова, ранги, комиссия.");
        l.add("config/ft_advisor_activities.json: список занятий, можно править и добавлять свои.");
        l.add("Команда /fta reset сбрасывает собранные данные аукциона.");
        return l;
    }

    @Override
    protected void init() {
        W = Math.min(520, width - 20);
        H = Math.min(380, height - 20);
        px = (width - W) / 2;
        py = (height - H) / 2;
        int inner = W - 40;

        // кнопки профиля
        int gap = 8;
        int bw = (inner - 2 * gap) / 3;
        btnGoal = new RoundedButton(px + 20, py + 40, bw, 20, Text.literal(""), b -> {
            Config.I.goal = (Config.I.goal + 1) % Config.GOALS.length;
            profileChanged();
        });
        btnRisk = new RoundedButton(px + 20 + bw + gap, py + 40, bw, 20, Text.literal(""), b -> {
            Config.I.risk = (Config.I.risk + 1) % Config.RISKS.length;
            profileChanged();
        });
        btnTime = new RoundedButton(px + 20 + 2 * (bw + gap), py + 40, bw, 20, Text.literal(""), b -> {
            Config.I.sessionTime = (Config.I.sessionTime + 1) % Config.TIMES.length;
            profileChanged();
        });
        updateProfileLabels();

        // поля
        double[] fr = {0.14, 0.24, 0.34, 0.28};
        int fgap = 14;
        int avail = inner - 3 * fgap;
        int x = px + 20;
        for (int i = 0; i < 4; i++) {
            fw[i] = (int) (avail * fr[i]);
            fx[i] = x;
            x += fw[i] + fgap;
        }
        int fy = py + 82;
        dayField = new TextFieldWidget(textRenderer, fx[0] + 6, fy, fw[0] - 12, 12, Text.literal("день"));
        balField = new TextFieldWidget(textRenderer, fx[1] + 6, fy, fw[1] - 12, 12, Text.literal("баланс"));
        searchField = new TextFieldWidget(textRenderer, fx[2] + 6, fy, fw[2] - 12, 12, Text.literal("поиск"));
        keyField = new TextFieldWidget(textRenderer, fx[3] + 6, fy, fw[3] - 12, 12, Text.literal("ключ"));
        for (TextFieldWidget f : new TextFieldWidget[]{dayField, balField, searchField, keyField}) f.setDrawsBackground(false);
        dayField.setMaxLength(3);
        dayField.setTextPredicate(s -> s.matches("\\d{0,3}"));
        dayField.setText(String.valueOf(Config.I.wipeDay));
        balField.setMaxLength(13);
        balField.setTextPredicate(s -> s.matches("\\d{0,13}"));
        balField.setPlaceholder(Text.literal("авто"));
        searchField.setMaxLength(40);
        searchField.setPlaceholder(Text.literal("элитры, ключ..."));
        keyField.setMaxLength(200);
        keyField.setText(Config.I.apiKey);
        keyField.setPlaceholder(Text.literal("необязательно"));
        searchField.setChangedListener(s -> {
            current = Tab.SEARCH;
            CONTENT.put(Tab.SEARCH, Advisor.search(s, balance()));
            rebuildRows();
            updateTabs();
        });

        // действия
        int by = py + 118;
        int abw = (inner - 2 * gap) / 3;
        btnAuction = new RoundedButton(px + 20, by, abw, 22, Text.literal("Анализ аукциона"), b -> {
            saveFields();
            long bal = balance();
            List<String> summary = Advisor.summary(bal);
            CONTENT.put(Tab.SUMMARY, summary);
            if (AuctionData.analyzed) {
                CONTENT.put(Tab.DEALS, Advisor.dealsText(bal));
                CONTENT.put(Tab.SEARCH, Advisor.search(searchField.getText(), bal));
            }
            current = Tab.SUMMARY;
            rebuildRows();
            updateTabs();
        });
        btnSituation = new RoundedButton(px + 20 + abw + gap, by, abw, 22, Text.literal("Анализ ситуации"), b -> {
            saveFields();
            replan();
            current = Tab.PLAN;
            rebuildRows();
            updateTabs();
        });
        btnAi = new RoundedButton(px + 20 + 2 * (abw + gap), by, abw, 22, Text.literal("Совет от ИИ"), b -> {
            saveFields();
            current = Tab.AI;
            if (Config.I.apiKey.isBlank()) {
                CONTENT.put(Tab.AI, List.of("##Нужен API-ключ", "Вставь ключ в поле «API-ключ» и нажми кнопку снова."));
            } else {
                CONTENT.put(Tab.AI, List.of("Запрос к ИИ, подожди несколько секунд..."));
                AiClient.ask(Advisor.aiPrompt(balance(), PlayerInfo.rank, Config.I.wipeDay)).thenAccept(text ->
                        client.execute(() -> {
                            CONTENT.put(Tab.AI, List.of(text.split("\n")));
                            if (current == Tab.AI) rebuildRows();
                        }));
            }
            rebuildRows();
            updateTabs();
        });

        // вкладки
        tabButtons.clear();
        Tab[] tabs = Tab.values();
        int tg = 5;
        int tw = (inner - (tabs.length - 1) * tg) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            final Tab t = tabs[i];
            RoundedButton tb = new RoundedButton(px + 20 + i * (tw + tg), py + 148, tw, 16, 6, Text.literal(t.label), b -> {
                current = t;
                rebuildRows();
                updateTabs();
            });
            tabButtons.add(tb);
        }

        drawOrder.clear();
        drawOrder.add(btnGoal); drawOrder.add(btnRisk); drawOrder.add(btnTime);
        drawOrder.add(dayField); drawOrder.add(balField); drawOrder.add(searchField); drawOrder.add(keyField);
        drawOrder.add(btnAuction); drawOrder.add(btnSituation); drawOrder.add(btnAi);
        drawOrder.addAll(tabButtons);
        for (ClickableWidget w : drawOrder) addDrawableChild(w);

        ax = px + 20;
        ay = py + 174;
        aw = W - 40;
        ah = H - 174 - 16;

        updateTabs();
        rebuildRows();
    }

    private void updateProfileLabels() {
        btnGoal.setMessage(Text.literal("Цель: " + Config.GOALS[Config.clamp(Config.I.goal, Config.GOALS.length)]));
        btnRisk.setMessage(Text.literal("Риск: " + Config.RISKS[Config.clamp(Config.I.risk, Config.RISKS.length)]));
        btnTime.setMessage(Text.literal("Время: " + Config.TIMES[Config.clamp(Config.I.sessionTime, Config.TIMES.length)]));
    }

    private void profileChanged() {
        updateProfileLabels();
        saveFields();
        if (CONTENT.containsKey(Tab.PLAN) && AuctionData.analyzed) {
            replan();
            rebuildRows();
        }
    }

    private void replan() {
        CONTENT.put(Tab.PLAN, Planner.plan(balance(), PlayerInfo.rank, Config.I.wipeDay));
    }

    private void updateTabs() {
        Tab[] tabs = Tab.values();
        for (int i = 0; i < tabButtons.size(); i++) tabButtons.get(i).selected = tabs[i] == current;
    }

    private void refreshButtons() {
        btnSituation.active = AuctionData.analyzed;
        btnAi.active = AuctionData.analyzed;
    }

    private long balance() {
        String t = balField == null ? "" : balField.getText().trim();
        if (!t.isEmpty()) {
            try { return Long.parseLong(t); } catch (NumberFormatException ignored) {}
        }
        return PlayerInfo.balance;
    }

    private void saveFields() {
        try { Config.I.wipeDay = Integer.parseInt(dayField.getText().trim()); } catch (NumberFormatException ignored) {}
        Config.I.apiKey = keyField.getText().trim();
        Config.save();
    }

    private void rebuildRows() {
        rows.clear();
        scroll = 0;
        List<String> lines = CONTENT.get(current);
        if (lines == null) {
            lines = List.of(current == Tab.PLAN ? "Нажми «Анализ ситуации» (после анализа аукциона)."
                    : "Здесь пока пусто. Нажми «Анализ аукциона».");
        }
        for (String s : lines) {
            boolean header = s.startsWith("##");
            String t = header ? s.substring(2) : s;
            int color = header ? 0xFFFFC857 : 0xFFD8DAE5;
            if (t.isEmpty()) { rows.add(new Row(OrderedText.EMPTY, color)); continue; }
            for (OrderedText ot : textRenderer.wrapLines(Text.literal(t), aw)) rows.add(new Row(ot, color));
        }
    }

    @Override
    public void removed() {
        saveFields();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float delta) {
        c.fill(0, 0, width, height, 0xB0000000);
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        renderBackground(c, mouseX, mouseY, delta);
        refreshButtons();

        Gfx.rrect(c, px - 2, py - 2, W + 4, H + 4, 16, 0xFF5865F2);
        Gfx.rrect(c, px, py, W, H, 14, 0xFF1B1D2A);

        c.drawText(textRenderer, "FunTime Advisor", px + 20, py + 14, 0xFFFFFFFF, true);
        c.drawText(textRenderer, "анализ аукциона и персональный план", px + 20, py + 25, 0xFF8A8FA8, false);

        String[] labels = {"День вайпа", "Баланс вручную", "Поиск предмета", "API-ключ"};
        for (int i = 0; i < 4; i++) {
            c.drawText(textRenderer, labels[i], fx[i], py + 68, 0xFFB8BBD0, false);
            Gfx.rrect(c, fx[i], py + 78, fw[i], 20, 6, 0xFF2A2D40);
        }

        // статус
        int sx = px + 20, sy = py + 104;
        String s1 = "Ранг: " + (PlayerInfo.rank.isEmpty() ? "не найден" : PlayerInfo.rank);
        c.drawText(textRenderer, s1, sx, sy, 0xFFFFFFFF, true);
        sx += textRenderer.getWidth(s1) + 14;
        long bal = PlayerInfo.balance;
        String s2 = "Баланс: " + (bal < 0 ? "не найден" : Advisor.money(bal));
        c.drawText(textRenderer, s2, sx, sy, 0xFF7CFC7C, true);
        sx += textRenderer.getWidth(s2) + 14;
        String s3 = "Лотов: " + AuctionData.size() + " (стр. " + AuctionData.pagesSeen + ")";
        c.drawText(textRenderer, s3, sx, sy, 0xFFFFFFFF, true);
        sx += textRenderer.getWidth(s3) + 14;
        c.drawText(textRenderer, AuctionData.analyzed ? "Анализ: готов" : "Анализ: нужен", sx, sy,
                AuctionData.analyzed ? 0xFF7CFC7C : 0xFFFF7C7C, true);

        // область результата
        Gfx.rrect(c, ax - 6, ay - 6, aw + 12, ah + 12, 10, 0xFF14151F);
        c.enableScissor(ax, ay, ax + aw, ay + ah);
        int y = ay - scroll;
        for (Row r : rows) {
            if (y + 10 >= ay && y <= ay + ah) c.drawText(textRenderer, r.text(), ax, y, r.color(), false);
            y += 10;
        }
        c.disableScissor();

        for (ClickableWidget w : drawOrder) w.render(c, mouseX, mouseY, delta);

        if (!btnSituation.active && (btnSituation.isHovered() || btnAi.isHovered()))
            c.drawText(textRenderer, "Сначала нажми «Анализ аукциона»", px + 20, py + H - 12, 0xFFFFC857, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        int max = Math.max(0, rows.size() * 10 - ah);
        scroll = (int) Math.max(0, Math.min(max, scroll - vertical * 14));
        return true;
    }
}
