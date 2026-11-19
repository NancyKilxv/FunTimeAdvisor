package dev.ftadvisor;

import net.minecraft.client.MinecraftClient;
import net.minecraft.scoreboard.*;
import net.minecraft.text.Text;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Читает баланс и ранг со скорборда (и запасной вариант из чата). */
public final class PlayerInfo {
    public static volatile long balance = -1;
    public static volatile String rank = "";
    private static boolean scoreboardBalance = false;

    private static final Pattern BAL = Pattern.compile("(?i)Монет\\D*([0-9][0-9,.\\s]*)");
    private static final Pattern RANK = Pattern.compile("(?i)Ранг\\s*:\\s*(.+)");
    private static final Pattern CHAT_BAL = Pattern.compile("(?i)баланс\\D*([0-9][0-9,.\\s]*)");

    public static void update(MinecraftClient mc) {
        if (mc.world == null) return;
        try {
            Scoreboard sb = mc.world.getScoreboard();
            ScoreboardObjective obj = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            if (obj == null) return;
            for (ScoreboardEntry e : sb.getScoreboardEntries(obj)) {
                Team team = sb.getScoreHolderTeam(e.owner());
                String line = Team.decorateName(team, e.name()).getString();
                Matcher m = BAL.matcher(line);
                if (m.find()) {
                    String d = m.group(1).replaceAll("[^0-9]", "");
                    if (!d.isEmpty()) { balance = Long.parseLong(d); scoreboardBalance = true; }
                }
                Matcher r = RANK.matcher(line);
                if (r.find()) rank = r.group(1).trim();
            }
        } catch (Exception ignored) {
        }
    }

    public static void onChat(String msg) {
        if (scoreboardBalance) return;
        Matcher m = CHAT_BAL.matcher(msg);
        if (m.find()) {
            String d = m.group(1).replaceAll("[^0-9]", "");
            if (!d.isEmpty() && d.length() < 16) balance = Long.parseLong(d);
        }
    }
}
