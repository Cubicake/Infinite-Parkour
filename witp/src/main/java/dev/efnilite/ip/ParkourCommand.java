package dev.efnilite.ip;

import dev.efnilite.ip.api.Gamemode;
import dev.efnilite.ip.api.Gamemodes;
import dev.efnilite.ip.config.Config;
import dev.efnilite.ip.config.Locales;
import dev.efnilite.ip.config.Option;
import dev.efnilite.ip.leaderboard.Leaderboard;
import dev.efnilite.ip.menu.Menus;
import dev.efnilite.ip.menu.community.SingleLeaderboardMenu;
import dev.efnilite.ip.player.ParkourPlayer;
import dev.efnilite.ip.player.ParkourUser;
import dev.efnilite.ip.player.data.InventoryData;
import dev.efnilite.ip.schematic.Schematic;
import dev.efnilite.ip.schematic.Schematics;
import dev.efnilite.ip.session.Session;
import dev.efnilite.ip.session.Session2;
import dev.efnilite.ip.util.Persistents;
import dev.efnilite.ip.util.Util;
import dev.efnilite.vilib.command.ViCommand;
import dev.efnilite.vilib.inventory.item.Item;
import dev.efnilite.vilib.particle.ParticleData;
import dev.efnilite.vilib.particle.Particles;
import dev.efnilite.vilib.util.Locations;
import dev.efnilite.vilib.util.Time;
import dev.efnilite.vilib.util.Version;
import org.bukkit.*;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;

@SuppressWarnings("deprecation")
public class ParkourCommand extends ViCommand {

    public static final HashMap<Player, Location[]> selections = new HashMap<>();

    private ItemStack wand;

    public ParkourCommand() {
        if (Version.isHigherOrEqual(Version.V1_14)) {
            wand = new Item(
                    Material.GOLDEN_AXE, "<dark_red><bold>IP Schematic Wand")
                    .lore("<gray>Left click: first position", "<gray>Right click: second position").build();
            Persistents.setPersistentData(wand, "ip", PersistentDataType.STRING, "true");
        }
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        String defaultLocale = (String) Option.OPTIONS_DEFAULTS.get(ParkourOption.LANG);

        Player player = null;
        if (sender instanceof Player) {
            player = (Player) sender;
        }

        if (args.length == 0) {
            // Main menu
            if (player == null) {
                sendHelpMessages(sender);
            } else if (ParkourOption.MAIN.check(player)) {
                Menus.MAIN.open(player);
            }
            return true;
        } else if (args.length == 1) {
            switch (args[0].toLowerCase()) {
                // Help menu
                case "help" -> {
                    sendHelpMessages(sender);
                    return true;
                }
                case "reload" -> {
                    if (!cooldown(sender, "reload", 2500)) {
                        return true;
                    }
                    if (!sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                        sender.sendMessage(defaultLocale, "other.no_do");
                        return true;
                    }
                    Time.timerStart("reloadIP");
                    Util.send(sender, IP.PREFIX + "Reloading config files..");

                    Config.reload();
                    Option.init(false);

                    Util.send(sender, IP.PREFIX + "Reloaded all config files in " + Time.timerEnd("reloadIP") + "ms!");
                    return true;
                }
                case "migrate" -> {
                    if (!cooldown(sender, "migrate", 2500)) {
                        return true;
                    }
                    if (!sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                        sender.sendMessage(defaultLocale, "other.no_do");
                        return true;
                    } else if (!Option.SQL) {
                        Util.send(sender, IP.PREFIX + "You have disabled SQL support in the config!");
                        return true;
                    }
                    Time.timerStart("migrate");
                    File folder = IP.getInFolder("players");

                    if (!folder.exists()) {
                        folder.mkdirs();
                        return true;
                    }

                    for (File file : folder.listFiles()) {
                        FileReader reader;
                        try {
                            reader = new FileReader(file);
                        } catch (FileNotFoundException ex) {
                            IP.logging().stack("Could not find file to migrate", ex);
                            Util.send(sender, IP.PREFIX + "<red>Could not find that file, try again!");
                            return true;
                        }
                        ParkourPlayer from = IP.getGson().fromJson(reader, ParkourPlayer.class);
                        String name = file.getName();
                        from.uuid = UUID.fromString(name.substring(0, name.lastIndexOf('.')));
                        from.save(true);
                    }
                    Util.send(sender, IP.PREFIX + "Your players' data has been migrated in " + Time.timerEnd("migrate") + "ms!");
                    return true;
                }
            }
            if (player == null) {
                return true;
            }
            switch (args[0]) {
                case "leaderboard" -> {
                    player.performCommand("ip leaderboard invalid");
                    return true;
                }
                case "join" -> {
                    if (!cooldown(sender, "join", 2500)) {
                        return true;
                    }

                    if (!ParkourOption.JOIN.check(player)) {
                        Util.send(sender, Locales.getString(defaultLocale, "other.no_do"));
                        return true;
                    }

                    ParkourUser user = ParkourUser.getUser(player);
                    if (user != null) {
                        return true;
                    }

                    Gamemodes.DEFAULT.create(player);
                    return true;
                }
                case "leave" -> {
                    if (!cooldown(sender, "leave", 2500)) {
                        return true;
                    }
                    ParkourUser.leave(player);

                    return true;
                }
                case "menu", "main" -> {
                    if (!ParkourOption.MAIN.check(player)) {
                        Menus.MAIN.open(player);
                    }
                    return true;
                }
                case "play" -> {
                    if (!ParkourOption.PLAY.check(player)) {
                        return true;
                    }
                    Menus.PLAY.open(player);

                    return true;
                }
                case "schematic" -> {
                    if (!player.hasPermission(ParkourOption.SCHEMATIC.getPermission())) { // default players shouldn't have access even if perms are disabled
                        Util.send(sender, Locales.getString(defaultLocale, "other.no_do"));
                        return true;
                    }
                    Util.send(player, "<dark_gray>----------- <dark_red><bold>Schematics <dark_gray>-----------");
                    Util.send(player, "");
                    Util.send(player, "<gray>Welcome to the schematic creating section.");
                    Util.send(player, "<gray>You can use the following commands:");
                    if (Version.isHigherOrEqual(Version.V1_14)) {
                        Util.send(player, "<red>/ip schematic wand <dark_gray>- <gray>Get the schematic wand");
                    }
                    Util.send(player, "<red>/ip schematic pos1 <dark_gray>- <gray>Set the first position of your selection");
                    Util.send(player, "<red>/ip schematic pos2 <dark_gray>- <gray>Set the second position of your selection");
                    Util.send(player, "<red>/ip schematic save <dark_gray>- <gray>Save your selection to a schematic file");
                    Util.send(player, "<red>/ip schematic paste <file> <dark_gray>- <gray>Paste a schematic file");
                    Util.send(player, "");
                    Util.send(player, "<dark_gray><underlined>Have any questions or need help? Join the Discord!");
                    return true;
                }
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("join") && sender instanceof Player) {
                if (!cooldown(sender, "join", 2500)) {
                    return true;
                }

                if (!ParkourOption.JOIN.check(player)) {
                    Util.send(sender, Locales.getString(defaultLocale, "other.no_do"));
                    return true;
                }

                String type = args[1]; // get mode from second arg
                Gamemode gamemode = IP.getRegistry().getGamemode(type);
                Session2 session = Session2.getSession(type.toUpperCase());

                if (gamemode == null) {
                    if (session == null) {
                        Util.send(sender, IP.PREFIX + "Unknown lobby! Try typing the code again."); // could not find, so go to default
                    } else {
                        ParkourUser user = ParkourUser.getUser(player);
                        if (user != null && user.getSession().getSessionId().equals(type.toUpperCase())) {
                            return true;
                        }

                        if (session.isAcceptingPlayers.apply(session)) {
                            Gamemodes.DEFAULT.create(player);
                        } else {
                            Gamemodes.SPECTATOR.create(player);
                        }
                    }
                } else {
                    gamemode.click(player);
                }
                return true;
            } else if (args[0].equalsIgnoreCase("schematic") && player != null && player.hasPermission(ParkourOption.ADMIN.getPermission())) {

                Location playerLocation = player.getLocation();
                Location[] existingSelection = selections.get(player);

                switch (args[1].toLowerCase()) {
                    case "wand" -> {
                        if (Version.isHigherOrEqual(Version.V1_14)) {
                            player.getInventory().addItem(wand);

                            Util.send(player, "<dark_gray>----------- <dark_red><bold>Schematics <dark_gray>-----------");
                            Util.send(player, "<gray>Use your IP Schematic Wand to easily select schematics.");
                            Util.send(player, "<gray>Use <dark_gray>left click<gray> to set the first position, and <dark_gray>right click <gray>for the second!");
                            Util.send(player, "<gray>If you can't place a block and need to set a position mid-air, use <dark_gray>the pos commands <gray>instead.");
                        }
                        return true;
                    }
                    case "pos1" -> {
                        Util.send(player, IP.PREFIX + "Position 1 was set to " + Locations.toString(playerLocation, true));

                        if (existingSelection == null) {
                            selections.put(player, new Location[] { playerLocation, null });
                            return true;
                        }

                        selections.put(player, new Location[] { playerLocation, existingSelection[1] });

                        Particles.box(BoundingBox.of(playerLocation, existingSelection[1]), player.getWorld(),
                                new ParticleData<>(Particle.END_ROD, null, 2), player, 0.2);
                        return true;
                    }
                    case "pos2" -> {
                        Util.send(player, IP.PREFIX + "Position 2 was set to " + Locations.toString(playerLocation, true));

                        if (existingSelection == null) {
                            selections.put(player, new Location[] { null, playerLocation });
                            return true;
                        }

                        selections.put(player, new Location[] { existingSelection[0], playerLocation });

                        Particles.box(BoundingBox.of(existingSelection[0], playerLocation), player.getWorld(),
                                new ParticleData<>(Particle.END_ROD, null, 2), player, 0.2);
                        return true;
                    }
                    case "save" -> {
                        if (!cooldown(sender, "schematic-save", 2500)) {
                            return true;
                        }

                        if (existingSelection == null || existingSelection[0] == null || existingSelection[1] == null) {
                            Util.send(player, "<dark_gray>----------- <dark_red><bold>Schematics <dark_gray>-----------");
                            Util.send(player, "<gray>Your schematic isn't complete yet.");
                            Util.send(player, "<gray>Be sure to set the first and second position!");
                            return true;
                        }

                        String code = Util.randomDigits(6);

                        Util.send(player, "<dark_gray>----------- <dark_red><bold>Schematics <dark_gray>-----------");
                        Util.send(player, "<gray>Your schematic is being saved..");
                        Util.send(player, "<gray>Your schematic will be generated with random number code <red>'" + code + "'<gray>!");
                        Util.send(player, "<gray>You can change the file name to whatever number you like.");
                        Util.send(player, "<dark_gray>Be sure to add this schematic to &r<dark_gray>schematics.yml!");

                        new Schematic(existingSelection[0], existingSelection[1])
                                .file("parkour-" + code)
                                .save(player);
                        return true;
                    }
                }
            } else if (args[0].equalsIgnoreCase("forcejoin") && args[1] != null && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {

                if (args[1].equalsIgnoreCase("everyone") && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                    for (Player other : Bukkit.getOnlinePlayers()) {
                        Gamemodes.DEFAULT.create(other);
                    }
                    Util.send(sender, IP.PREFIX + "Successfully force joined everyone!");
                    return true;
                }

                if (args[1].equalsIgnoreCase("nearest")) {
                    Player closest = null;
                    double distance = Double.MAX_VALUE;

                    // if player is found get location from player
                    // if no player is found, get location from command block
                    // if no command block is found, return null
                    Location from = sender instanceof Player ? player.getLocation() : (sender instanceof BlockCommandSender ? ((BlockCommandSender) sender).getBlock().getLocation() : null);

                    if (from == null || from.getWorld() == null) {
                        return true;
                    }

                    // get the closest player
                    for (Player p : from.getWorld().getPlayers()) {
                        double d = p.getLocation().distance(from);

                        if (d < distance) {
                            distance = d;
                            closest = p;
                        }
                    }

                    // no closest player found
                    if (closest == null) {
                        return true;
                    }

                    Util.send(sender, IP.PREFIX + "Successfully force joined " + closest.getName() + "!");
                    Gamemodes.DEFAULT.create(closest);
                    return true;
                }

                Player other = Bukkit.getPlayer(args[1]);
                if (other == null) {
                    Util.send(sender, IP.PREFIX + "That player isn't online!");
                    return true;
                }

                Gamemodes.DEFAULT.create(other);
                return true;
            } else if (args[0].equalsIgnoreCase("forceleave") && args[1] != null && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {

                if (args[1].equalsIgnoreCase("everyone") && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                    for (ParkourPlayer other : ParkourUser.getActivePlayers()) {
                        ParkourUser.leave(other);
                    }
                    Util.send(sender, IP.PREFIX + "Successfully force kicked everyone!");
                    return true;
                }

                Player other = Bukkit.getPlayer(args[1]);
                if (other == null) {
                    Util.send(sender, IP.PREFIX + "That player isn't online!");
                    return true;
                }

                ParkourUser user = ParkourUser.getUser(other);
                if (user == null) {
                    Util.send(sender, IP.PREFIX + "That player isn't currently playing!");
                    return true;
                }

                ParkourUser.leave(user);
                return true;
            } else if (args[0].equalsIgnoreCase("recoverinventory") && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                if (!cooldown(sender, "recoverinventory", 2500)) {
                    return true;
                }
                Player arg1 = Bukkit.getPlayer(args[1]);
                if (arg1 == null) {
                    Util.send(sender, IP.PREFIX + "That player isn't online!");
                    return true;
                }

                InventoryData data = new InventoryData(arg1);
                data.readFile(readData -> {
                    if (readData != null) {
                        Util.send(sender, IP.PREFIX + "Successfully recovered the inventory of " + arg1.getName() + " from their file");
                        if (readData.apply(true)) {
                            Util.send(sender, IP.PREFIX + "Giving " + arg1.getName() + " their items now...");
                        } else {
                            Util.send(sender, IP.PREFIX + "<red>There was an error decoding an item of " + arg1.getName());
                            Util.send(sender, IP.PREFIX + "" + arg1.getName() + "'s file has been manually edited or has no saved inventory. " +
                                    "Check the console for more information.");
                        }
                    } else {
                        Util.send(sender, IP.PREFIX + "<red>There was an error recovering the inventory of " + arg1.getName() + " from their file");
                        Util.send(sender, IP.PREFIX + arg1.getName() + " has no saved inventory or there was an error. Check the console.");
                    }
                });
            } else if (args[0].equalsIgnoreCase("reset") && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                if (!cooldown(sender, "reset", 2500)) {
                    return true;
                }

                if (args[1].equalsIgnoreCase("everyone") && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                    for (Gamemode gamemode : IP.getRegistry().getGamemodes()) {
                        Leaderboard leaderboard = gamemode.getLeaderboard();

                        if (leaderboard == null) {
                            continue;
                        }

                        leaderboard.resetAll();
                    }

                    Util.send(sender, IP.PREFIX + "Successfully reset all high scores in memory and the files.");
                } else {
                    String name = null;
                    UUID uuid = null;

                    // Check online players
                    Player online = Bukkit.getPlayerExact(args[1]);
                    if (online != null) {
                        name = online.getName();
                        uuid = online.getUniqueId();
                    }

                    // Check uuid
                    if (args[1].contains("-")) {
                        uuid = UUID.fromString(args[1]);
                    }

                    // Check offline player
                    if (uuid == null) {
                        OfflinePlayer offline = Bukkit.getOfflinePlayer(args[1]);
                        name = offline.getName();
                        uuid = offline.getUniqueId();
                    }

                    UUID finalUuid = uuid;
                    String finalName = name;

                    for (Gamemode gamemode : IP.getRegistry().getGamemodes()) {
                        Leaderboard leaderboard = gamemode.getLeaderboard();

                        if (leaderboard == null) {
                            continue;
                        }

                        leaderboard.reset(finalUuid);
                    }

                    Util.send(sender, IP.PREFIX + "Successfully reset the high score of " + finalName + " in memory and the files.");
                }

                return true;
            } else if (args[0].equalsIgnoreCase("leaderboard")) {

                // check permissions
                if (!ParkourOption.LEADERBOARDS.check(player)) {
                    Util.send(sender, Locales.getString(defaultLocale, "other.no_do"));
                    return true;
                }

                Gamemode gamemode = IP.getRegistry().getGamemode(args[1].toLowerCase());

                // if found gamemode is null, return to default
                if (gamemode == null) {
                    Menus.LEADERBOARDS.open(player);
                } else {
                    Menus.SINGLE_LEADERBOARD.open(player, gamemode, SingleLeaderboardMenu.Sort.SCORE);
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("schematic") && player != null && player.hasPermission(ParkourOption.ADMIN.getPermission())) {
                if (args[1].equalsIgnoreCase("paste")) {
                    String name = args[2];
                    Schematic schematic = Schematics.getSchematic(name);
                    if (schematic == null) {
                        Util.send(sender, IP.PREFIX + "Couldn't find " + name);
                        return true;
                    }

                    schematic.paste(player.getLocation());
                    Util.send(sender, IP.PREFIX + "Pasted schematic " + name);
                    return true;
                }
            }
        }
        return true;
    }
    
    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            if (ParkourOption.JOIN.check(sender)) {
                completions.add("join");
                completions.add("leave");
            }
            if (ParkourOption.MAIN.check(sender)) {
                completions.add("menu");
            }
            if (ParkourOption.PLAY.check(sender)) {
                completions.add("play");
            }
            if (ParkourOption.LEADERBOARDS.check(sender)) {
                completions.add("leaderboards");
            }
            if (sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                completions.add("schematic");
                completions.add("reload");
                completions.add("forcejoin");
                completions.add("forceleave");
                completions.add("reset");
                completions.add("recoverinventory");
                completions.add("migrate");
            }
            return completions(args[0], completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("reset") && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                completions.add("everyone");
                for (ParkourPlayer pp : ParkourUser.getActivePlayers()) {
                    completions.add(pp.getName());
                }
            } else if (args[0].equalsIgnoreCase("schematic") && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                completions.addAll(Arrays.asList("wand", "pos1", "pos2", "save", "paste"));
            } else if (args[0].equalsIgnoreCase("forcejoin") && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                completions.add("nearest");
                completions.add("everyone");

                for (Player pl : Bukkit.getOnlinePlayers()) {
                    completions.add(pl.getName());
                }
            } else if (args[0].equalsIgnoreCase("forceleave") && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                completions.add("everyone");

                for (Player pl : Bukkit.getOnlinePlayers()) {
                    completions.add(pl.getName());
                }
            } else if (args[0].equalsIgnoreCase("recoverinventory") && sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
                for (Player pl : Bukkit.getOnlinePlayers()) {
                    completions.add(pl.getName());
                }
            }
            return completions(args[1], completions);
        } else {
            return Collections.emptyList();
        }
    }

    public static void sendHelpMessages(CommandSender sender) {
        Util.send(sender, "");
        Util.send(sender, "<dark_gray><strikethrough>---------------<reset> " + IP.NAME + " <dark_gray><strikethrough>---------------<reset>");
        Util.send(sender, "");
        Util.send(sender, "<gray>/parkour <dark_gray>- Main command");
        if (sender.hasPermission(ParkourOption.JOIN.getPermission())) {
            Util.send(sender, "<gray>/parkour join [mode] <dark_gray>- Join the default gamemode or specify a mode.");
            Util.send(sender, "<gray>/parkour leave <dark_gray>- Leave the game on this server");
        }
        if (sender.hasPermission(ParkourOption.MAIN.getPermission())) {
            Util.send(sender, "<gray>/parkour menu <dark_gray>- Open the menu");
        }
        if (sender.hasPermission(ParkourOption.PLAY.getPermission())) {
            Util.send(sender, "<gray>/parkour play <dark_gray>- Mode selection menu");
        }
        if (sender.hasPermission(ParkourOption.LEADERBOARDS.getPermission())) {
            Util.send(sender, "<gray>/parkour leaderboard [type]<dark_gray>- Open the leaderboard of a gamemode");
        }
        if (sender.hasPermission(ParkourOption.ADMIN.getPermission())) {
            Util.send(sender, "<gray>/ip schematic <dark_gray>- Create a schematic");
            Util.send(sender, "<gray>/ip reload <dark_gray>- Reloads the messages-v3.yml file");
            Util.send(sender, "<gray>/ip migrate <dark_gray>- Migrate your JSON files to MySQL");
            Util.send(sender, "<gray>/ip reset <everyone/player> <dark_gray>- Resets all highscores. <red>This can't be recovered!");
            Util.send(sender, "<gray>/ip forcejoin <everyone/nearest/player> <dark_gray>- Forces a specific player, the nearest or everyone to join");
            Util.send(sender, "<gray>/ip forceleave <everyone/nearest/player> <dark_gray>- Forces a specific player, the nearest or everyone to leave");
            Util.send(sender, "<gray>/ip recoverinventory <player> <dark_gray>- Recover a player's saved inventory." +
                    " <red>Useful for recovering data after server crashes or errors when leaving.");
        }
        Util.send(sender, "");

    }
}