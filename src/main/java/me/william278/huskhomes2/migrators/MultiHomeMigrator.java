package me.william278.huskhomes2.migrators;

import me.william278.huskhomes2.HuskHomes;
import me.william278.huskhomes2.util.MessageManager;
import me.william278.huskhomes2.data.DataManager;
import me.william278.huskhomes2.teleport.points.Home;
import me.william278.huskhomes2.teleport.points.TeleportationPoint;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.bukkit.Bukkit;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Level;

public class MultiHomeMigrator {

    public enum MultiHome {
        // writer.write("# <username>;<x>;<y>;<z>;<pitch>;<yaw>;<world>[;<name>]" +
        // Util.newLine());
        UserName, X, Y, Z, Pitch, Yaw, World, Name
    }

    private static final HuskHomes plugin = HuskHomes.getInstance();

    public static void migrate() {
        migrate(null, HuskHomes.getSettings().getServerID());
    }

    // Migrate data from EssentialsX
    public static void migrate(final String worldFilter, final String targetServer) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HuskHomes.backupDatabase();
            final File homesTxtFile = new File(Bukkit.getWorldContainer() + File.separator + "plugins" + File.separator
                    + "MultiHome" + File.separator + "homes.txt");

            if (homesTxtFile.exists()) {
                plugin.getLogger().info("MultiHome plugin data found!");
                if (worldFilter != null) {
                    plugin.getLogger().info("Started Filtered Migration from MultiHome:\n• World to migrate from: "
                            + worldFilter + "\n• Target server: " + targetServer);
                } else {
                    plugin.getLogger().info("Started Migration from MultiHome...");
                }

                Iterable<CSVRecord> homes = null;
                try {
                    Reader in = new FileReader(homesTxtFile.getAbsolutePath());
                    final CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT).setCommentMarker('#')
                            .setHeader(MultiHome.class).setSkipHeaderRecord(true).setDelimiter(";")
                            .setAllowMissingColumnNames(true).build();
                    plugin.getLogger().info(csvFormat.getHeader().toString());
                    homes = csvFormat.parse(in);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to parse data from MultiHome/homes.txt!");
                }

                if (homes == null) {
                    plugin.getLogger().info("No homes found :(");
                    return;
                }

                for (CSVRecord home : homes) {
                    try {
                        final String playerName = home.get(MultiHome.UserName);
                        final String worldName = home.get(MultiHome.World);

                        if (worldFilter != null) {
                            if (!worldFilter.equalsIgnoreCase(worldName)) {
                                continue;
                            }
                        }

                        @SuppressWarnings("deprecation")
                        UUID playerUUID = Bukkit.getOfflinePlayer(playerName).getUniqueId();
                        final UUID offlinePlayerUUID = UUID
                                .nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes(StandardCharsets.UTF_8));

                        if (playerUUID.compareTo(offlinePlayerUUID) == 0) {
                            // uuid is offlineplayer, try fetch new uuid, or ignore it
                            String foundUuid = getUuidForOldPlayer(playerName);
                            if(foundUuid != null) {
                                playerUUID = UUID.fromString(foundUuid);
                            } else {
                                plugin.getLogger().warning("✖ Could not migrate: " + home.toString()
                                + " as the player UUID was a offline-UUID, and was not found in online api either");
                                continue;
                            }
                        }

                        try {
                            try (Connection connection = HuskHomes.getConnection()) {
                                if (!DataManager.playerExists(playerUUID, connection)) {
                                    DataManager.createPlayer(playerUUID, playerName, connection);
                                }

                                try {
                                    String homeName = home.get(MultiHome.Name);
                                    if (homeName == null || homeName.length() < 1) {
                                        homeName = "default";
                                    }
                                    final double x = Double.parseDouble(home.get(MultiHome.X));
                                    final double y = Double.parseDouble(home.get(MultiHome.Y));
                                    final double z = Double.parseDouble(home.get(MultiHome.Z));
                                    final float pitch = Float.parseFloat(home.get(MultiHome.Pitch));
                                    final float yaw = Float.parseFloat(home.get(MultiHome.Yaw));
                                    final String homeDescription = MessageManager
                                            .getRawMessage("home_default_description", playerName);

                                    if (DataManager.homeExists(playerName, homeName, connection)) {
                                        plugin.getLogger()
                                                .warning("✖ Failed to migrate home " + homeName + " (Already exists!)");
                                        continue;
                                    }

                                    DataManager.addHome(
                                            new Home(
                                                    new TeleportationPoint(worldName, x, y, z, yaw, pitch,
                                                            targetServer),
                                                    playerName, playerUUID, homeName, homeDescription, false,
                                                    Instant.now().getEpochSecond()),
                                            playerUUID, connection);

                                    plugin.getLogger().info("→ Migrated home for: " + playerName + "/" + homeName
                                            + " in world: " + worldName);
                                } catch (NullPointerException | IllegalArgumentException e) {
                                    plugin.getLogger().warning("✖ Failed to migrate home: " + home.toString() + "!");
                                    e.printStackTrace();
                                }
                            } catch (SQLException e) {
                                plugin.getLogger().log(Level.SEVERE,
                                        "An SQL exception occurred migrating Essentials home data.", e);
                            }

                        } catch (NullPointerException ignored) {
                        }
                    } catch (IllegalArgumentException ignored) {
                    }
                }

            } else {
                plugin.getLogger().warning("Failed to Migrate from MultiHome/homes.txt!");
            }
            try {
                Thread.sleep(10L);
            } catch (InterruptedException ignored) {
            }
        });
    }


    public static String getUuidForOldPlayer(String name) {
        String url = "https://api.ashcon.app/mojang/v2/user/" + name;
        try {
            @SuppressWarnings("deprecation")
            String UUIDJson = IOUtils.toString(new URL(url));     

            if(UUIDJson.isEmpty()) {
                return null;
            }

            JSONObject UUIDObject = (JSONObject) JSONValue.parseWithException(UUIDJson);
            String currentName = UUIDObject.get("username").toString();
            if(!name.equalsIgnoreCase(currentName)) {
                //changed name, ignore
                return null;
            }
            String uuidstr = UUIDObject.get("uuid").toString();
            return uuidstr;
        } catch (IOException | ParseException ignore) {
        }
       
        return null;
    }
}
