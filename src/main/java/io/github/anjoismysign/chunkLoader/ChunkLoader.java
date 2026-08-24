package io.github.anjoismysign.chunkLoader;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public final class ChunkLoader extends JavaPlugin {

    private final Queue<ChunkPos> queue = new ArrayDeque<>();

    private World world;

    private int chunksPerTick;
    private int minChunkX;
    private int maxChunkX;
    private int minChunkZ;
    private int maxChunkZ;

    private int totalChunks;
    private int loadedChunks;

    private boolean loadingFinished = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        chunksPerTick = Math.max(1, getConfig().getInt("chunks-per-tick", 5));
        String worldName = getConfig().getString("world.name");
        if (worldName == null || worldName.isEmpty()) {
            getLogger().severe("world.name is not configured!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        world = Bukkit.getWorld(worldName);
        if (world == null) {
            getLogger().severe("World '" + worldName + "' could not be found!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        int pos1X = getConfig().getInt("world.pos1.x");
        int pos1Z = getConfig().getInt("world.pos1.z");
        int pos2X = getConfig().getInt("world.pos2.x");
        int pos2Z = getConfig().getInt("world.pos2.z");
        /*
         * Convert block coordinates into chunk coordinates.
         *
         * >> 4 is equivalent to floor(x / 16) for positive coordinates,
         * but Math.floorDiv also works correctly for negative coordinates.
         */
        int chunk1X = Math.floorDiv(pos1X, 16);
        int chunk1Z = Math.floorDiv(pos1Z, 16);
        int chunk2X = Math.floorDiv(pos2X, 16);
        int chunk2Z = Math.floorDiv(pos2Z, 16);

        minChunkX = Math.min(chunk1X, chunk2X);
        maxChunkX = Math.max(chunk1X, chunk2X);
        minChunkZ = Math.min(chunk1Z, chunk2Z);
        maxChunkZ = Math.max(chunk1Z, chunk2Z);

        buildQueue();
        totalChunks = queue.size();
        getLogger().info(
                "Map chunk loader starting: " +
                        totalChunks + " chunks (" +
                        minChunkX + ".." + maxChunkX + ", " +
                        minChunkZ + ".." + maxChunkZ + ")"
        );
        getLogger().info("Loading " + chunksPerTick + " new chunks per tick.");
        startLoader();
    }

    private void buildQueue() {
        int centerX = (minChunkX + maxChunkX) / 2;
        int centerZ = (minChunkZ + maxChunkZ) / 2;

        ArrayDeque<ChunkPos> sorted = new ArrayDeque<>();
        List<ChunkPos> positions = new ArrayList<>();
        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                positions.add(new ChunkPos(x, z));
            }
        }
        positions.sort((a, b) -> {
            long da = distanceSquared(a.x, a.z, centerX, centerZ);
            long db = distanceSquared(b.x, b.z, centerX, centerZ);
            return Long.compare(da, db);
        });
        sorted.addAll(positions);
        queue.addAll(sorted);
    }

    private long distanceSquared(
            int x,
            int z,
            int centerX,
            int centerZ) {
        long dx = x - centerX;
        long dz = z - centerZ;
        return dx * dx + dz * dz;
    }

    private void startLoader() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!processQueue()) {
                    return;
                }
                cancel();
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private boolean processQueue() {
        if (loadingFinished) {
            return true;
        }
        int requestsThisTick = 0;
        while (requestsThisTick < chunksPerTick && !queue.isEmpty()) {
            ChunkPos pos = queue.poll();
            requestsThisTick++;
            loadChunk(pos);
        }
        if (queue.isEmpty()) {
            /*
             * Don't immediately consider loading finished here.
             *
             * getChunkAtAsync completes later, so we wait until
             * every requested chunk has actually completed.
             */
            if (loadedChunks >= totalChunks) {
                finishLoading();
            }
        }
        return false;
    }

    private void loadChunk(ChunkPos pos) {
        world.getChunkAtAsync(pos.x, pos.z, false)
                .thenAccept(chunk -> {
                    /*
                     * Bukkit API operations should happen on the main thread.
                     *
                     * The CompletableFuture resolves when the chunk has loaded,
                     * but we schedule the ticket operation onto the server thread.
                     */
                    Bukkit.getScheduler().runTask(
                            this,
                            () -> {
                                if (!isEnabled()) {
                                    return;
                                }
                                world.addPluginChunkTicket(
                                        pos.x,
                                        pos.z,
                                        this
                                );
                                loadedChunks++;
                                if (queue.isEmpty() && loadedChunks >= totalChunks) {
                                    finishLoading();
                                }
                            }
                    );

                }).exceptionally(throwable -> {
                    getLogger().severe("Failed to load chunk " + pos.x + ", " + pos.z + ": " + throwable.getMessage());
                    return null;
                });
    }

    private void finishLoading() {
        if (loadingFinished) {
            return;
        }
        loadingFinished = true;
        getLogger().info("========================================");
        getLogger().info("Map chunk loading complete!");
        getLogger().info("Loaded and ticketed " + loadedChunks + " chunks.");
        getLogger().info("The map will remain loaded until shutdown.");
        getLogger().info("========================================");
    }

    @Override
    public void onDisable() {
        if (world != null) {
            world.removePluginChunkTickets(this);
        }
        queue.clear();
    }

    private record ChunkPos(int x, int z) {
    }
}