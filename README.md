# chunk-loader

A lightweight Paper/Spigot plugin that force-loads and permanently keeps a
rectangular region of a world in memory, using async chunk loading spread
across ticks so the server doesn't stall on startup.

Chunks are loaded outward from the center of the configured region and kept
loaded for as long as the plugin is enabled (via a plugin chunk ticket).
When the plugin is disabled, all tickets are released.

## Requirements

- Paper (or a Paper-API compatible server) `26.2` or newer
- Java 25

## Building

```
mvn clean package
```

The shaded jar is produced at `target/chunk-loader-1.0.jar`. Drop it into
your server's `plugins/` folder.

## Configuration (`config.yml`)

On first run, the plugin copies its default `config.yml` into
`plugins/chunk-loader/config.yml`. It looks like this:

```yaml
chunks-per-tick: 5
world:
  name: world
  pos1:
    x: -100
    z: -100
  pos2:
    x: 100
    z: 100
```

### `chunks-per-tick`

How many chunks to request per server tick while the initial load is in
progress. Higher values load the region faster but put more load on the
server during startup; lower values are gentler but take longer to finish.
Values below `1` are clamped up to `1`.

### `world.name`

The name of the world to load chunks in (this is the folder name of the
world, e.g. `world`, `world_nether`, `world_the_end`). If the world isn't
found when the plugin enables (for example, it hasn't been loaded yet, or
the name is misspelled), the plugin logs an error and disables itself.

### `world.pos1` / `world.pos2`

Two opposite corners of the region to load, given in **block coordinates**
(x/z only — chunk loading covers the full height of the world, so there's
no y coordinate). The plugin converts these into chunk coordinates and
loads every chunk in the rectangle they define. The order of the corners
doesn't matter — the plugin automatically works out the min/max bounds.

For example, the default config loads every chunk between block
`(-100, -100)` and `(100, 100)`, which comes out to chunks `(-7..6, -7..6)`.

## Behavior notes

- Loading starts automatically in `onEnable` and is one-shot: chunks are
  requested a batch at a time (`chunks-per-tick` per tick) until the whole
  region has been requested and every request has completed.
- Chunks are ordered by distance from the center of the region outward, so
  the "middle" of your area becomes usable first.
- Once loading finishes, a summary is logged and the plugin does nothing
  further — the region simply stays loaded via chunk tickets until the
  server (or the plugin) shuts down.
- Reloading the plugin's config at runtime has no effect; the region is
  only (re)computed on enable. Restart or reload the plugin to apply
  config changes.
