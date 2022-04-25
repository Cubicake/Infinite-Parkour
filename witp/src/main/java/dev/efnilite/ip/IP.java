package dev.efnilite.ip;

import dev.efnilite.ip.api.Registry;
import dev.efnilite.ip.events.Handler;
import dev.efnilite.ip.generator.DefaultGenerator;
import dev.efnilite.ip.generator.base.GeneratorOption;
import dev.efnilite.ip.hook.MultiverseHook;
import dev.efnilite.ip.hook.PlaceholderHook;
import dev.efnilite.ip.internal.gamemode.DefaultGamemode;
import dev.efnilite.ip.internal.gamemode.SpectatorGamemode;
import dev.efnilite.ip.internal.style.DefaultStyleType;
import dev.efnilite.ip.player.ParkourPlayer;
import dev.efnilite.ip.player.ParkourUser;
import dev.efnilite.ip.reward.RewardReader;
import dev.efnilite.ip.util.UpdateChecker;
import dev.efnilite.ip.util.config.Configuration;
import dev.efnilite.ip.util.config.Option;
import dev.efnilite.ip.util.sql.SQLManager;
import dev.efnilite.ip.world.WorldDivider;
import dev.efnilite.ip.world.WorldHandler;
import dev.efnilite.vilib.ViPlugin;
import dev.efnilite.vilib.util.Logging;
import dev.efnilite.vilib.util.Task;
import dev.efnilite.vilib.util.Time;
import dev.efnilite.vilib.util.Version;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Main class of Infinite Parkour
 *
 * @author Efnilite
 * Copyright (c) 2020-2022
 */
public final class IP extends ViPlugin {

    public static final String NAME = "<gradient:#B30000>Infinite Parkour</gradient:#00A1A1>";
    public static final String PREFIX = NAME + " <#7B7B7B>» <gray>";

    public static boolean OUTDATED = false;
    private static IP instance;
    private static SQLManager sqlManager;
    private static Registry registry;
    private static WorldDivider divider;
    private static WorldHandler worldHandler;
    private static Configuration configuration;

    @Nullable
    private static MultiverseHook multiverseHook;

    @Nullable
    private static PlaceholderHook placeholderHook;

    @Override
    public void enable() {
        // ----- Instance and timing -----

        instance = this;
        Time.timerStart("load");

        // ----- Configurations -----

        configuration = new Configuration(this);

        Option.init(true);

        divider = new WorldDivider();

        // ----- Hooks and Bungee -----

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            logging().info("Connecting with PlaceholderAPI..");
            placeholderHook = new PlaceholderHook();
            placeholderHook.register();
        }
        if (getServer().getPluginManager().isPluginEnabled("Multiverse-Core")) {
            logging().info("Connecting with Multiverse..");
            multiverseHook = new MultiverseHook();
        }
        if (Option.BUNGEECORD.get()) {
            getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        }

        // ----- Worlds -----

        worldHandler = new WorldHandler();
        worldHandler.createWorld();

        // ----- Registry -----

        registry = new Registry();

        registry.register(new DefaultGamemode());
        registry.register(new SpectatorGamemode());
        registry.registerType(new DefaultStyleType());

        registry.getStyleType("default").addConfigStyles("styles.list", configuration.getFile("config"));

        // ----- SQL and data -----

        try {
            if (Option.SQL.get()) {
                sqlManager = new SQLManager();
                sqlManager.connect();
            }
            ParkourUser.initHighScores();
        } catch (Throwable throwable) {
            logging().stack("There was an error while starting WITP", throwable);
        }

        // ----- Events -----

        registerListener(new Handler());
        registerCommand("witp", new ParkourCommand());

        // ----- Update checker -----

        if (Option.UPDATE_CHECKER.get()) {
            UpdateChecker checker = new UpdateChecker();

            Task.create(IP.getPlugin())
                    .repeat(8 * 72000) // 8 hours
                    .execute(checker::check)
                    .run();
        }

        // ----- Metrics -----

        Metrics metrics = new Metrics(this, 9272);
        metrics.addCustomChart(new SimplePie("using_sql", () -> Boolean.toString(Option.SQL.get())));
        metrics.addCustomChart(new SimplePie("using_logs", () -> Boolean.toString(Option.GAMELOGS.get())));
        metrics.addCustomChart(new SimplePie("using_rewards", () -> Boolean.toString(RewardReader.REWARDS_ENABLED.get())));
        metrics.addCustomChart(new SimplePie("locale_count", () -> Integer.toString(Option.LANGUAGES.get().size())));
        metrics.addCustomChart(new SingleLineChart("player_joins", () -> {
            int joins = ParkourUser.JOIN_COUNT;
            ParkourUser.JOIN_COUNT = 0;
            return joins;
        }));

        logging().info("Loaded WITP in " + Time.timerEnd("load") + "ms!");
    }

    @Override
    public void disable() {
        for (ParkourUser user : ParkourUser.getUsers()) {
            ParkourUser.unregister(user, true, false, false);
        }

        if (sqlManager != null) {
            sqlManager.close();
        }

        if (divider != null) { // somehow this can be null despite it only ever being set to a new instance?
            World world = worldHandler.getWorld();
            if (world != null) {
                for (Player player : world.getPlayers()) {
                    player.kickPlayer("Server is restarting");
                }
            }
        } else {
            World world = Bukkit.getWorld(Option.WORLD_NAME.get());
            if (world != null) {
                for (Player player : world.getPlayers()) {
                    player.kickPlayer("Server is restarting");
                }
            }
        }

        worldHandler.deleteWorld();
    }

    /**
     * Gets a DefaultGenerator which disables schematics if the version is below 1.16.
     *
     * @param   player
     *          The player
     *
     * @return a {@link DefaultGenerator}
     */
    public static DefaultGenerator getVersionGenerator(ParkourPlayer player) {
        if (versionSupportsSchematics()) {
            return new DefaultGenerator(player);
        } else {
            return new DefaultGenerator(player, GeneratorOption.DISABLE_SCHEMATICS);
        }
    }

    /**
     * Checks whether the current version supports schematics
     *
     * @return true if it supports it, false if not.
     */
    public static boolean versionSupportsSchematics() {
        return Version.isHigherOrEqual(Version.V1_16);
    }

    /**
     * Returns the {@link Logging} belonging to this plugin.
     *
     * @return this plugin's {@link Logging} instance.
     */
    public static Logging logging() {
        return getPlugin().logging;
    }

    /**
     * Returns this plugin instance.
     *
     * @return the plugin instance.
     */
    public static IP getPlugin() {
        return instance;
    }

    // Static stuff
    @Nullable
    public static MultiverseHook getMultiverseHook() {
        return multiverseHook;
    }

    @Nullable
    public static PlaceholderHook getPlaceholderHook() {
        return placeholderHook;
    }

    public static WorldHandler getWorldHandler() {
        return worldHandler;
    }

    public static Registry getRegistry() {
        return registry;
    }

    public static SQLManager getSqlManager() {
        return sqlManager;
    }

    public static WorldDivider getDivider() {
        return divider;
    }

    public static Configuration getConfiguration() {
        return configuration;
    }
}