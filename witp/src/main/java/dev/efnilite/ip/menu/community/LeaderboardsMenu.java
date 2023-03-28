package dev.efnilite.ip.menu.community;

import dev.efnilite.ip.IP;
import dev.efnilite.ip.api.Gamemode;
import dev.efnilite.ip.config.Locales;
import dev.efnilite.ip.config.Option;
import dev.efnilite.ip.menu.Menus;
import dev.efnilite.ip.menu.ParkourOption;
import dev.efnilite.ip.player.ParkourUser;
import dev.efnilite.ip.util.Util;
import dev.efnilite.vilib.inventory.PagedMenu;
import dev.efnilite.vilib.inventory.item.Item;
import dev.efnilite.vilib.inventory.item.MenuItem;
import dev.efnilite.vilib.util.Unicodes;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Leaderboards menu
 */
public class LeaderboardsMenu {

    public void open(Player player) {
        ParkourUser user = ParkourUser.getUser(player);
        String locale = user == null ? (String) Option.OPTIONS_DEFAULTS.get(ParkourOption.LANG) : user.getLocale();

        PagedMenu gamemode = new PagedMenu(3, Locales.getString(player, ParkourOption.LEADERBOARDS.path + ".name"));

        Gamemode latest = null;
        List<MenuItem> items = new ArrayList<>();
        for (Gamemode gm : IP.getRegistry().getModes()) {
            if (gm.getLeaderboard() == null || !gm.isVisible()) {
                continue;
            }

            Item item = gm.getItem(locale);
            items.add(item.clone().click(event -> {
                if (gm.getName().equals("time-trial") || gm.getName().equals("duels")) {
                    Menus.SINGLE_LEADERBOARD.open(player, gm, SingleLeaderboardMenu.Sort.TIME);
                } else {
                    Menus.SINGLE_LEADERBOARD.open(player, gm, SingleLeaderboardMenu.Sort.SCORE);
                }
            }));
            latest = gm;
        }

        if (items.size() == 1) {
            Menus.SINGLE_LEADERBOARD.open(player, latest, SingleLeaderboardMenu.Sort.SCORE);
            return;
        }

        gamemode.displayRows(0, 1).addToDisplay(items)

                .nextPage(26, new Item(Material.LIME_DYE, "<#0DCB07><bold>" + Unicodes.DOUBLE_ARROW_RIGHT) // next page
                        .click(event -> gamemode.page(1)))

                .prevPage(18, new Item(Material.RED_DYE, "<#DE1F1F><bold>" + Unicodes.DOUBLE_ARROW_LEFT) // previous page
                        .click(event -> gamemode.page(-1)))

                .item(22, Locales.getItem(player, "other.close").click(event -> Menus.COMMUNITY.open(event.getPlayer())))

                .fillBackground(Util.isBedrockPlayer(player) ? Material.AIR : Material.WHITE_STAINED_GLASS_PANE)
                .open(player);
    }
}