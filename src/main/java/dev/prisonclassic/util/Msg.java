package dev.prisonclassic.util;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public final class Msg {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static FileConfiguration cfg;

    private Msg() {
    }

    public static void init(FileConfiguration var0) {
        cfg = var0;
    }

    public static Component parse(String var0, TagResolver... var1) {
        return MM.deserialize(var0, var1);
    }

    public static Component msg(String var0, TagResolver... var1) {
        String var2 = cfg.getString("messages.prefix", "");
        String var3 = cfg.getString("messages." + var0, "<red>Нет сообщения: " + var0);
        return MM.deserialize(var2 + var3, var1);
    }

    public static void send(CommandSender var0, String var1, TagResolver... var2) {
        var0.sendMessage(msg(var1, var2));
    }

    public static TagResolver p(String var0, Object var1) {
        return Placeholder.unparsed(var0, String.valueOf(var1));
    }

    public static TagResolver pm(String var0, String var1) {
        return Placeholder.parsed(var0, var1);
    }

    public static String money(double var0) {
        String[] var2 = new String[]{"", "K", "M", "B", "T", "Q"};
        int var3 = 0;

        double var4;
        for (var4 = var0; Math.abs(var4) >= 1000.0 && var3 < var2.length - 1; var3++) {
            var4 /= 1000.0;
        }

        return String.format(Locale.US, "%.2f", var4) + var2[var3];
    }
}
