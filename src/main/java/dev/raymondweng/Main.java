package dev.raymondweng;

import io.github.cdimascio.dotenv.Dotenv;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.InteractionContextType;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class Main {
    public static JDA jda;
    public static final Dotenv dotenv = Dotenv.configure().directory("./env/.env").load();
    public static final MessageController messageController = new MessageController();

    public static void main(String[] args) throws InterruptedException {
        // Java Discord API
        jda = JDABuilder
                .createDefault(dotenv.get("DISCORD_BOT_TOKEN"))
                .addEventListeners(messageController)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                .build();
        jda.awaitReady();
        jda.upsertCommand(
                Commands.slash("summary", "得知最近發生了什麼")
                        .setContexts(InteractionContextType.GUILD)
        ).queue();
        jda.upsertCommand(
                Commands
                        .slash("monitor", "[暫時僅開發者]開始監控這個頻道（並自動讀取部分歷史訊息）")
                        .addOption(OptionType.STRING, "channel_id", "要監控的頻道ID（預設為當前頻道）", false)
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        ).queue();
        jda.upsertCommand(
                Commands
                        .slash("stop", "[開發者]停止監控")
                        .addOption(OptionType.STRING, "channel_id", "要停止監控的頻道ID（預設為當前頻道）", false)
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        ).queue();
        jda.upsertCommand(
                Commands
                        .slash("list", "[開發者]列出所有監控中頻道")
                        .setContexts(InteractionContextType.GUILD)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        ).queue();
        jda.getPresence().setActivity(Activity.playing("在監控中的頻道/summary"));
    }
}
