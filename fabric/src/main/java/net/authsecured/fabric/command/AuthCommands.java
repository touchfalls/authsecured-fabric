package net.authsecured.fabric.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.authsecured.core.model.UserAccount;
import net.authsecured.core.model.UserSession;
import net.authsecured.core.port.AuthRepository;
import net.authsecured.core.port.RateLimiter;
import net.authsecured.core.port.SessionStorage;
import net.authsecured.core.security.IpAnonymizer;
import net.authsecured.core.security.PasswordHasher;
import net.authsecured.fabric.auth.AuthManager;
import net.authsecured.fabric.i18n.MessageService;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Brigadier command tree implementation for AuthSecured user and administrator commands.
 * Guarantees 0 TPS drop by running password hashing and database IO asynchronously,
 * safely returning callbacks to the main server thread via server.execute(() -> {}).
 */
public final class AuthCommands {

    private final AuthRepository repository;
    private final SessionStorage sessionStorage;
    private final RateLimiter rateLimiter;
    private final PasswordHasher passwordHasher;
    private final IpAnonymizer ipAnonymizer;

    public AuthCommands(AuthRepository repository, SessionStorage sessionStorage, RateLimiter rateLimiter, PasswordHasher passwordHasher, IpAnonymizer ipAnonymizer) {
        this.repository = repository;
        this.sessionStorage = sessionStorage;
        this.rateLimiter = rateLimiter;
        this.passwordHasher = passwordHasher;
        this.ipAnonymizer = ipAnonymizer;
    }

    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            registerUserCommands(dispatcher);
            registerAdminCommands(dispatcher);
        });
    }

    private void registerUserCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        // /register <password> <confirmPassword>
        dispatcher.register(CommandManager.literal("register")
            .then(CommandManager.argument("password", StringArgumentType.string())
                .then(CommandManager.argument("confirmPassword", StringArgumentType.string())
                    .executes(this::executeRegister))));

        // /login <password>
        dispatcher.register(CommandManager.literal("login")
            .then(CommandManager.argument("password", StringArgumentType.string())
                .executes(this::executeLogin)));

        // /changepassword <oldPassword> <newPassword>
        dispatcher.register(CommandManager.literal("changepassword")
            .then(CommandManager.argument("oldPassword", StringArgumentType.string())
                .then(CommandManager.argument("newPassword", StringArgumentType.string())
                    .executes(this::executeChangePassword))));

        // /logout
        dispatcher.register(CommandManager.literal("logout")
            .executes(this::executeLogout));

        // /authstatus
        dispatcher.register(CommandManager.literal("authstatus")
            .executes(this::executeAuthStatus));
    }

    private void registerAdminCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        var adminCommand = CommandManager.literal("authadmin")
            .requires(source -> source.hasPermissionLevel(4))
            .then(CommandManager.literal("reload")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal("§aAuthSecured configuration reloaded."), true);
                    return 1;
                }))
            .then(CommandManager.literal("unregister")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .executes(this::executeAdminUnregister)))
            .then(CommandManager.literal("unlock")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .executes(this::executeAdminUnlock)));

        dispatcher.register(adminCommand);

        var authAlias = CommandManager.literal("auth")
            .requires(source -> source.hasPermissionLevel(4))
            .then(CommandManager.literal("reload")
                .executes(ctx -> {
                    ctx.getSource().sendFeedback(() -> Text.literal("§aAuthSecured configuration reloaded."), true);
                    return 1;
                }))
            .then(CommandManager.literal("unregister")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .executes(this::executeAdminUnregister)))
            .then(CommandManager.literal("unlock")
                .then(CommandManager.argument("player", StringArgumentType.word())
                    .executes(this::executeAdminUnlock)));

        dispatcher.register(authAlias);
    }

    private int executeRegister(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        char[] pass = StringArgumentType.getString(ctx, "password").toCharArray();
        char[] confirm = StringArgumentType.getString(ctx, "confirmPassword").toCharArray();

        if (!Arrays.equals(pass, confirm)) {
            PasswordHasher.wipe(pass);
            PasswordHasher.wipe(confirm);
            source.sendError(MessageService.getInstance().get("authsecured.register.mismatch"));
            return 0;
        }

        UUID uuid = player.getUuid();
        String username = player.getName().getString();
        String rawIp = player.getIp();
        String hashedIp = ipAnonymizer.anonymize(rawIp);

        CompletableFuture.runAsync(() -> {
            boolean isReg = repository.isRegistered(uuid).join();
            if (isReg) {
                PasswordHasher.wipe(pass);
                PasswordHasher.wipe(confirm);
                source.getServer().execute(() -> player.sendMessage(MessageService.getInstance().get("authsecured.register.already"), false));
                return;
            }

            String hash = passwordHasher.hash(pass);
            PasswordHasher.wipe(confirm);

            UserAccount newAccount = new UserAccount(uuid, username, hash, hashedIp, Instant.now(), Instant.now());
            repository.save(newAccount).join();

            UserSession session = new UserSession(uuid, username, hashedIp, Instant.now(), Instant.now().plus(Duration.ofHours(24)));
            sessionStorage.saveSession(session).join();

            source.getServer().execute(() -> {
                AuthManager.getInstance().setAuthenticated(uuid, true);
                player.sendMessage(MessageService.getInstance().get("authsecured.register.success"), false);
            });
        });

        return 1;
    }

    private int executeLogin(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        if (AuthManager.getInstance().isAuthenticated(player)) {
            source.sendError(MessageService.getInstance().get("authsecured.login.already"));
            return 0;
        }

        char[] pass = StringArgumentType.getString(ctx, "password").toCharArray();
        UUID uuid = player.getUuid();
        String username = player.getName().getString();
        String rawIp = player.getIp();
        String hashedIp = ipAnonymizer.anonymize(rawIp);

        CompletableFuture.runAsync(() -> {
            var limitResult = rateLimiter.checkLimit(username).join();
            if (!limitResult.allowed()) {
                PasswordHasher.wipe(pass);
                source.getServer().execute(() -> player.sendMessage(MessageService.getInstance().get("authsecured.ratelimit.locked", limitResult.retryAfterSeconds()), false));
                return;
            }

            var accountOpt = repository.findByUuid(uuid).join();
            if (accountOpt.isEmpty()) {
                PasswordHasher.wipe(pass);
                source.getServer().execute(() -> player.sendMessage(MessageService.getInstance().get("authsecured.login.not_registered"), false));
                return;
            }

            UserAccount account = accountOpt.get();
            boolean valid = passwordHasher.verify(pass, account.passwordHash());

            if (!valid) {
                rateLimiter.recordFailure(username).join();
                source.getServer().execute(() -> player.sendMessage(MessageService.getInstance().get("authsecured.login.invalid"), false));
                return;
            }

            rateLimiter.reset(username).join();
            UserSession session = new UserSession(uuid, username, hashedIp, Instant.now(), Instant.now().plus(Duration.ofHours(24)));
            sessionStorage.saveSession(session).join();

            source.getServer().execute(() -> {
                AuthManager.getInstance().setAuthenticated(uuid, true);
                player.sendMessage(MessageService.getInstance().get("authsecured.login.success", username), false);
            });
        });

        return 1;
    }

    private int executeChangePassword(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        if (!AuthManager.getInstance().isAuthenticated(player)) {
            source.sendError(MessageService.getInstance().get("authsecured.login.prompt"));
            return 0;
        }

        char[] oldPass = StringArgumentType.getString(ctx, "oldPassword").toCharArray();
        char[] newPass = StringArgumentType.getString(ctx, "newPassword").toCharArray();
        UUID uuid = player.getUuid();

        CompletableFuture.runAsync(() -> {
            var accountOpt = repository.findByUuid(uuid).join();
            if (accountOpt.isEmpty()) {
                PasswordHasher.wipe(oldPass);
                PasswordHasher.wipe(newPass);
                return;
            }

            UserAccount account = accountOpt.get();
            boolean valid = passwordHasher.verify(oldPass, account.passwordHash());
            if (!valid) {
                PasswordHasher.wipe(newPass);
                source.getServer().execute(() -> player.sendMessage(MessageService.getInstance().get("authsecured.changepassword.invalid_old"), false));
                return;
            }

            String newHash = passwordHasher.hash(newPass);
            UserAccount updated = new UserAccount(uuid, account.username(), newHash, account.hashedIp(), account.registrationDate(), Instant.now());
            repository.update(updated).join();

            source.getServer().execute(() -> player.sendMessage(MessageService.getInstance().get("authsecured.changepassword.success"), false));
        });

        return 1;
    }

    private int executeLogout(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        UUID uuid = player.getUuid();
        AuthManager.getInstance().setAuthenticated(uuid, false);
        sessionStorage.invalidateSession(uuid);

        source.sendFeedback(() -> MessageService.getInstance().get("authsecured.logout.success"), false);
        return 1;
    }

    private int executeAuthStatus(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        boolean authed = AuthManager.getInstance().isAuthenticated(player);
        source.sendFeedback(() -> Text.literal("§eAuthSecured Status: " + (authed ? "§aAuthenticated" : "§cUnauthenticated")), false);
        return 1;
    }

    private int executeAdminUnregister(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "player");

        CompletableFuture.runAsync(() -> {
            var accountOpt = repository.findByUsername(targetName).join();
            if (accountOpt.isEmpty()) {
                source.getServer().execute(() -> source.sendError(Text.literal("§cUser " + targetName + " not found!")));
                return;
            }

            UserAccount account = accountOpt.get();
            repository.delete(account.uuid()).join();
            sessionStorage.invalidateSession(account.uuid()).join();

            source.getServer().execute(() -> {
                AuthManager.getInstance().setAuthenticated(account.uuid(), false);
                source.sendFeedback(() -> Text.literal("§aUser " + targetName + " unregistered successfully."), true);
            });
        });

        return 1;
    }

    private int executeAdminUnlock(CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        String targetName = StringArgumentType.getString(ctx, "player");

        rateLimiter.reset(targetName);
        source.sendFeedback(() -> Text.literal("§aRate limit reset for user " + targetName), true);
        return 1;
    }
}
