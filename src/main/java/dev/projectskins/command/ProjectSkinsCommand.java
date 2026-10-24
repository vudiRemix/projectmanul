package dev.projectskins.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.projectskins.network.ProjectSkinsNetwork;
import dev.projectskins.network.SkinSyncPacket;
import dev.projectskins.server.LocalSkinCatalog;
import dev.projectskins.server.ServerSkinRegistry;
import dev.projectskins.skin.SkinSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * Серверная команда выбора скина. Требует уровень прав 2 (оператор либо консоль).
 * Валидирует все аргументы до изменения реестра, затем рассылает назначение всем
 * клиентам. Для URL подтверждает именно отправку назначения: загрузка изображения
 * выполняется позже отдельно на каждом клиенте и может завершиться ошибкой сети.
 */
public final class ProjectSkinsCommand {
    private static final DynamicCommandExceptionType INVALID_ARGUMENT =
            new DynamicCommandExceptionType(message -> Component.literal(String.valueOf(message)));
    private static final SimpleCommandExceptionType NO_PERMISSION =
            new SimpleCommandExceptionType(Component.literal("Недостаточно прав: нужен уровень оператора 2"));

    private ProjectSkinsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                ServerSkinRegistry registry, LocalSkinCatalog catalog) {
        dispatcher.register(Commands.literal("projectskins")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("arguments", StringArgumentType.greedyString())
                        .suggests((context, builder) -> suggest(context, builder, catalog))
                        .executes(context -> execute(context, registry, catalog))));
    }

    private static int execute(CommandContext<CommandSourceStack> context,
                               ServerSkinRegistry registry, LocalSkinCatalog catalog)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!source.hasPermission(2)) {
            throw NO_PERMISSION.create();
        }

        SkinCommandArguments arguments;
        SkinSource skin;
        try {
            arguments = SkinCommandArguments.parse(StringArgumentType.getString(context, "arguments"));
            skin = SkinSource.parse(arguments.source());
            if (skin.type() == SkinSource.Type.LOCAL) {
                catalog.require(skin.value());
            }
        } catch (IllegalArgumentException exception) {
            throw INVALID_ARGUMENT.create(exception.getMessage());
        }

        ServerPlayer target = source.getServer().getPlayerList().getPlayerByName(arguments.nickname());
        if (target == null) {
            throw INVALID_ARGUMENT.create("Игрок '" + arguments.nickname() + "' сейчас не в сети");
        }

        SkinSyncPacket packet = registry.assign(target.getUUID(), skin);
        ProjectSkinsNetwork.broadcast(packet);
        source.sendSuccess(() -> Component.literal(
                "Скин " + skin.value() + " назначен игроку " + target.getName().getString()
                        + ". Обновление отправлено всем игрокам."
                        + (skin.type() == SkinSource.Type.URL ? " Изображение загрузится на клиентах." : "")), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> context,
                                                           SuggestionsBuilder builder,
                                                           LocalSkinCatalog catalog) {
        String remaining = builder.getRemaining();
        int firstSpace = -1;
        for (int index = 0; index < remaining.length(); index++) {
            if (Character.isWhitespace(remaining.charAt(index))) {
                firstSpace = index;
                break;
            }
        }
        if (firstSpace < 0) {
            return SharedSuggestionProvider.suggest(catalog.ids(), builder);
        }
        int nicknameStart = firstSpace;
        while (nicknameStart < remaining.length() && Character.isWhitespace(remaining.charAt(nicknameStart))) {
            nicknameStart++;
        }
        SuggestionsBuilder nicknameBuilder = builder.createOffset(builder.getStart() + nicknameStart);
        return SharedSuggestionProvider.suggest(
                Arrays.asList(context.getSource().getServer().getPlayerList().getPlayerNamesArray()),
                nicknameBuilder);
    }
}
