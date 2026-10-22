# rexlCloud

rexlCloud is a cloud system for Minecraft networks: a master manages groups of servers and proxies, and wrappers on one or more machines start them. Servers start on the wrapper with the most free memory (wrappers with a busy CPU only when nothing else fits). Templates live on the master; a changed file is sent to all wrappers within seconds.

## Features

Here are some of the most important features of rexlCloud:

- **Automatic and dynamic server starting**: Depending on the number of online players.
- **Automatic and dynamic virtual machine selection**: Selects a virtual machine with enough available resources for the instance being started.
- **Powerful API**: Synchronized in real-time across the entire network and offering async methods with callbacks. All commands have a corresponding API method.
- **Web panel and REST API**: The optional RestModule serves the API for the web panel, over HTTPS if you like.
- **Multi-Root**: Use one Master instance with as many Wrappers as you like.
- **Multi-Proxy**: Can start not only Bukkit/Spigot servers, but also BungeeCord/Velocity proxies.
- **Paper, Spigot, Velocity and BungeeCord built in**: Paper, Velocity and BungeeCord (its newest build) are downloaded, Spigot is built with SpigotMC's BuildTools the first time a version is used (needs git; the jar is then cached in `cache/spigot/`).
- **Your own server software**: Groups can run a custom `server.jar`, e.g. a Minestom server with the MinestomBridge.

## Download

Every version is a [GitHub release](https://github.com/rexlCloud/rexlCloud/releases) with the jars
`launcher.jar`, `master.jar`, `wrapper.jar`, `bridge.jar`, `proxy.jar` and `restmodule.jar`, each with a
`.sha256` checksum. You only need the launcher:

```bash
curl -L -o launcher.jar https://github.com/rexlCloud/rexlCloud/releases/latest/download/launcher.jar
```

The launcher downloads the master and wrapper from the newest release, the wrapper the cloud plugins (`bridge.jar`,
`proxy.jar`) of its own version, and the master installed official modules such as the RestModule. Every download
is checked against its checksum. The system property `rexlcloud.releases` (e.g.
`-Drexlcloud.releases=https://github.com/you/rexlCloud/releases`) points them to the releases of another repository,
e.g. a fork.

The APIs for developers (`module-api`, `bukkit-api`, `proxy-api`, `minestom-api`) are Maven artifacts on
`https://repo.rexlcloud.de/releases`, see [API documentation](#api-documentation).

### Java versions

Master and wrappers need Java 25. The cloud plugin for Paper/Spigot servers (Bridge) runs on Java 21 or newer
and only uses Bukkit API that exists since 1.8.8, so it works on every version from Paper 1.8.8 to the newest.
Servers older than 1.20.5 need Java 21 for the plugin: pick a Java 21 runtime for their group (Paper 1.8.8 runs
on it). Servers older than 1.13 have no modern forwarding, so networks with them need `forwardingMode` LEGACY.
The proxy plugin targets Velocity 4, which itself needs Java 25.

### Encryption

The connection between master and wrappers is encrypted with TLS 1.3. On its first start the master creates a key
and a self-signed certificate in `config/tls/` and logs its fingerprint (`TLS fingerprint of the master: ...`).
A wrapper checks that fingerprint on every connection:

- `masterFingerprint` in the wrapper's `config/wrapper.json` set: only exactly that master is accepted. Recommended,
  the master also shows the value after `wrapper add`.
- empty: the first master the wrapper reaches is trusted and its fingerprint saved (like SSH); compare it with
  the one the master logged. From then on any other certificate is refused.

If the master's `config/tls/` is deleted it creates a new certificate, and every wrapper needs the new
fingerprint. The connection between a wrapper and its servers stays on 127.0.0.1 and is not encrypted.

### Protocol and plugin data

Master, wrappers and servers talk length-prefixed JSON; master and wrappers must have the same version (the launcher
updates both on restart), and servers get the matching cloud plugin when they start. A wrapper of another version is
disconnected with "does not speak the cloud protocol".

Plugins using `MessageChannel` or `Player.getData` can only send JDK value types (strings, numbers, collections,
time types) and their own classes; anything else is refused, because deserializing arbitrary classes from the
network can run code.

## Setup

The installation guide is on [rexlcloud.de](https://rexlcloud.de/docs/installation). In short: start the launcher
once as master (`java -jar launcher.jar --master`) and once as wrapper (`--wrapper`), then register the wrapper with
the `wrapper add` command it prints.

On its first start (no `config/master.json` yet) the master asks a few questions in its console; Enter takes the
suggested answer and Tab shows the choices:

- the port the wrappers connect to,
- the proxy software: Velocity (modern forwarding) or BungeeCord (legacy forwarding),
- the database: the built-in H2, or MySQL/MariaDB with host, port, database, user and password. The connection is
  tested right away and the database created if the user may; if it fails, try again, change the settings or use
  H2,
- which optional official modules to install, e.g. the RestModule for the web panel.

Ctrl+C cancels without saving anything. Without console input (stdin closed) the suggested answers are used, and
`--no-setup` skips the questions, e.g. when a script writes the configuration files itself.

## Restarts and updates

Restarting or updating the **master** does not restart the network: the wrappers keep their servers running,
connect again within seconds and report them, and the master continues with them (proxies report their players
again). For up to a minute after its start the master waits for its wrappers before it starts new servers. If a
wrapper's connection breaks without the master restarting, its servers are kept for two minutes in case it comes
back.

Restarting a **wrapper** normally stops the servers on it, because they are its child processes; needed servers
start again. To keep them running through wrapper restarts and updates too, set `"detachedServers": true` in the
wrapper's `config/wrapper.json` (Linux, needs tmux). Each server then runs in its own tmux session (socket
`rexlcloud-<serverPort>`, e.g. `tmux -L rexlcloud-2243 ls`); the wrapper remembers them in `servers/running.json`
and attaches to them again when it starts. Their plugins connect again by themselves; a server whose wrapper stays
away for two minutes shuts down. In this mode leaving the wrapper (the `detach` command, an update, `systemctl
stop`) keeps the servers running; only `stop` stops them.
If the wrapper runs as a systemd service, set `KillMode=process` in its unit: by default systemd ends every
process the service started when it stops, which includes the servers' tmux sessions.

The launcher updates itself: with `auto_update` it downloads a newer launcher and uses it from the next start. Where the running jar cannot be replaced (Windows) it is saved as `launcher.jar.new`.

## Wrappers and memory

A server starts on the wrapper with the most free memory that has synced templates, the group's Java runtime and
room for the group's memory. Servers that are still starting (or came online less than a minute ago) count with
their full memory, so several servers starting at once do not all land on the same wrapper. If no wrapper has
enough, the server waits and the master logs it once a minute.

Servers connect to their wrapper on 127.0.0.1 port 2243. Two wrappers on one machine need different ports:
set `serverPort` in the wrapper's `config/wrapper.json`. A server's plugin proves which server it is with a
secret the wrapper writes into the server's `cloud.json` for every start (readable only by the user running the
wrapper); other connections are refused, and API requests always count as coming from the plugin's own server.

New installs start with maintenance mode off (`maintenance` in `config/master.json`).

## Database

The master keeps players and statistics in a database, set in `config/database.json`:

- New installs use an embedded H2 database (`"type": "h2"`) in the master's `database/` folder. Nothing to set up;
  good for a network on one or a few machines.
- For MySQL or MariaDB set `"type": "mysql"` with `host`, `port`, `user`, `password` and `database`.
  A `database.json` without `type` uses MySQL.

`database backup` in the master console writes a backup of the embedded database into `database/backups/`.
Restore one by stopping the master and unpacking it into `database/`. For MySQL use mysqldump or your host's
backups.

To move to another database (e.g. from MySQL to H2 or the other way), put its settings into
`config/database-target.json` (same format as `database.json`) and run `database copy` in the master console. It
copies players, their data and the statistics into the target, which has to be empty. Then stop the master, rename
`database-target.json` to `database.json` and start it again.

## Modules

Modules are jars in the master's `modules/` folder. The official ones that ship with the cloud are optional:
the first-start setup offers them, `modules available` lists them and `modules install <name>` downloads one in
the master's version and loads it (or put its jar there yourself). They are listed in `OfficialModule.ALL` in the
master; a new official module only needs an entry there and its jar in the release assets
(`.github/workflows/release.yml`). On every start the master brings installed official modules to its own version
from the GitHub release of that version; modules that are not installed are never added, and third-party modules are
left alone. If the download fails, the installed version is loaded.

On its first start the RestModule creates the user `admin` with a random password. It is shown in the console
(never written to the log files) and saved in `modules/RestModule/admin-password.txt`, readable only by the user
running the master; the file is deleted when the admin changes the password.

## Web panel API over HTTPS

The RestModule serves plain HTTP by default. For HTTPS, either put a reverse proxy (nginx, Caddy) in front of it,
or let it serve HTTPS itself in `modules/RestModule/config.json`:

```json
"tls": {
  "enabled": true,
  "certificate": "/etc/letsencrypt/live/cloud.example.com/fullchain.pem",
  "privateKey": "/etc/letsencrypt/live/cloud.example.com/privkey.pem"
}
```

Paths are absolute or relative to `modules/RestModule/`. The key must be PKCS#8 (`BEGIN PRIVATE KEY`, which
certbot writes); convert others with `openssl pkcs8 -topk8 -nocrypt -in key.pem -out privkey.pem`. The files are
checked every minute and a renewed certificate is used without a restart. The user running the master must be
able to read them; certbot's folders are root-only, so copy them in a deploy hook if needed. If HTTPS is enabled
and the certificate cannot be loaded, the API does not start instead of falling back to HTTP. Enter the URL with
`https://` on the panel's login page.

## Metrics

The RestModule can serve metrics in the Prometheus format at `/api/v2/metrics`: players online, maintenance,
servers per group and state, players per server, and per wrapper whether it is connected, its CPU load and memory.
Set a random `metricsToken` in `modules/RestModule/config.json` (empty turns the endpoint off) and let Prometheus
send it:

```yaml
scrape_configs:
  - job_name: rexlcloud
    metrics_path: /api/v2/metrics
    authorization:
      credentials: <metricsToken>
    static_configs:
      - targets: ["cloud.example.com:4343"]
```

## Templates

The master's `templates/` folder is the source of every template; the wrappers keep a copy in
`servers/templates/`:

- `templates/<group>/default/` (and other variants next to it) for each group,
- `templates/global/server/` and `templates/global/proxy/` for files every server or proxy gets, e.g. plugins.
  `global` is therefore not allowed as a group name.

A new server gets the global template, then `default/`, then one **variant**: every other folder next to
`default/` (for example `map-desert/`, `map-forest/`) is a variant, and each server picks one at random. Later
layers replace files of earlier ones. The chosen variant is in the server's `cloud.json` as `TemplateVariant`.

When a wrapper connects, it reports what it has (size and SHA-256 of every file); the master sends only what is
missing or changed and deletes files and templates it no longer has. No server starts on a wrapper before that.
Changes in `templates/` are sent to all wrappers about two seconds after the last change. Files are encrypted with
the wrapper's key, sent in 1 MB chunks and only replace the old file once the checksum matches.
`group sync template <group>` (or "Sync template" in the panel) makes all wrappers report again and repairs files
that were changed on a wrapper by hand. Static groups are not synced; their files stay on their wrapper.

### Saving a server into a template

`server save <server> [variant]` in the master console, "Save to template" on the server page of the panel, or
`POST /api/v2/servers/<server>/save-template` copies the files of a running server (worlds, configs, plugins) into
`templates/<group>/<variant>/` (default `default`), replacing files with the same path and keeping the others. The
worlds are saved first. Logs, caches, libraries, the server jar, the cloud plugin and `cloud.json` are left out.
A new variant name makes a new variant, e.g. after building a map on a server.

### Template files in the panel

The "Templates" page of the panel shows the templates of all groups and the global ones: browse them, edit text
files, upload files (e.g. a plugin or the `server.jar` of a custom jar group, up to 512 MB), download, create
folders and variants and delete. It needs the permission MANAGE_TEMPLATES (admins get it automatically). Through the
API: `GET /api/v2/templates`, and `/api/v2/templates/<group>/<variant>/files|file|folder?path=...`.

## Server list (MOTD)

The proxies show the MOTD from the cloud's messages (`MOTD`, and `MAINTENANCE_MOTD` while maintenance mode is on),
written in MiniMessage with `<newline>` for the second line; classic `&` colour codes work too. Change it on the
"Server list" page of the web panel (with a preview), in `config/messages.json` of the master, or with
`PUT /api/v2/motd`. Changes apply with the next server list refresh, no restart needed. Messages sent to players
from the panel use MiniMessage as well.

## Custom server jars (Minestom)

A group can run your own `server.jar` instead of Paper or Spigot: choose **Custom jar** in the panel, `CUSTOM` in
`group create`, or `"server_jar": "CUSTOM"` in the API. Nothing is downloaded. Put the jar into
`templates/<group>/default/server.jar` on the master (for static groups into `servers/templates/<group>/default/`
on their wrapper); the group's servers wait until it is there. The wrapper starts it with `java -jar server.jar`
and the group's memory and Java arguments, and adds no cloud plugin, no Paper/Spigot configuration and no start
arguments. Everything the server needs is in the `cloud.json` in its folder: name, port, forwarding mode and secret,
and the token for the wrapper.

For Minestom servers the **MinestomBridge** reads that and connects the server to the cloud. Add it to the server
(`de.rexlcloud:minestom-api` from `https://repo.rexlcloud.de/releases`, same version as the cloud):

```java
var cloud = RexlCloudBridge.getInstance();
var server = MinecraftServer.init(cloud.getAuth());   // Velocity/BungeeCord forwarding like the proxies
// ... instances and events ...
cloud.initialize();                                   // connects to the wrapper; the cloud API is now usable
server.start("0.0.0.0", cloud.getPort());
```

Without a `cloud.json` (started by hand) it runs offline on port 25565 and does not connect. When the cloud stops
the server or its wrapper goes away, the server stops; change that with `setShutdownHook`. A complete example is
in `MinestomBridge/example`. Rebuild the server when you update the cloud, because the bridge has to match the
cloud's version.

## Java runtime per group

Every wrapper looks for installed Java runtimes (the usual install folders, the home folder, `JAVA_HOME`, `PATH`
and the folders in `javaSearchFolders` of its `config/wrapper.json`) and reports them to the master. Each group
picks one: in the web panel on the group page, with `group edit <group> java <path>` in the master console, or with
`PATCH /api/v2/groups/<group>` and `java_command`. `java` means the one on the wrapper's `PATH`. Servers of a group
only start on wrappers that have its runtime, and only reported runtimes are accepted.

A server that crashes is reported to the master, which starts a replacement. If servers of a group keep crashing
right after starting (for example because the Java version is too old), the wrapper waits longer each time,
up to five minutes.

## Player forwarding

Proxies pass each player's real UUID and address on to the servers behind them. The wrapper writes the
settings into `velocity.toml`, BungeeCord's `config.yml`, `spigot.yml` and Paper's configuration before every start.
Choose the mode in the master's `config/master.json`:

| `forwardingMode` | Use it for | Notes |
| --- | --- | --- |
| `MODERN` (default) | Velocity with Paper 1.13+ | Protected by `forwardingSecret`, generated on the first start. Servers cannot be joined without the proxy. |
| `LEGACY` | BungeeCord proxies, Spigot, or servers older than 1.13 | BungeeCord forwarding. Anyone who can reach a server port directly can join as any player, so firewall the server ports. |

Restart the servers after changing it.

## API documentation

The APIs for modules and plugins are published to `https://repo.rexlcloud.de/releases` with their Javadoc
(`-javadoc.jar`, which IDEs download with the sources):

| Artifact | For |
|----------|-----|
| `de.rexlcloud:module-api` | Cloud modules (`CloudModule`, `ModuleApi`), and the shared API (`RexlCloudApi`, players, servers, events, message channels) |
| `de.rexlcloud:bukkit-api` | Paper/Spigot plugins using the Bridge |
| `de.rexlcloud:proxy-api` | Velocity and BungeeCord plugins |
| `de.rexlcloud:minestom-api` | Minestom servers of custom jar groups |

`gradlew apiDocs` builds all four as HTML into `build/api-docs/`.

## Building from source

If you want to build rexlCloud from source, follow these instructions:

````gradle
git clone https://github.com/rexlCloud/rexlCloud.git
cd rexlCloud
gradlew build
`````

`e2e/run.sh` then starts a master with the RestModule and a wrapper from the built jars, creates a Paper lobby
through the API, and checks that it comes online, restarts and is removed with its group. It needs Linux, curl, jq
and a MySQL database (see the top of the script for the settings). CI runs it before every release.

## Support

If you need help with rexlCloud, please join our [Discord server](https://discord.gg/WrhDX4E).