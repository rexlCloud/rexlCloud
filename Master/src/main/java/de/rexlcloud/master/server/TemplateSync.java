package de.rexlcloud.master.server;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.AesUtil;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.TemplateDeletePacket;
import de.rexlcloud.core.network.packets.TemplateFilePacket;
import de.rexlcloud.core.network.packets.TemplateStateRequestPacket;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.files.RecursiveFolderWatcher;
import de.rexlcloud.core.server.template.TemplateFile;
import de.rexlcloud.core.server.template.Templates;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.wrapper.Wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps the templates of the wrappers equal to the master's {@code templates/} folder.
 * <p>
 * After connecting, a wrapper reports what it has; the master then sends only missing or changed files (in chunks)
 * and deletes files and templates it no longer has. Until that first sync is done, no servers start on the wrapper.
 * Changes in {@code templates/} are sent to all wrappers a moment after the last change.
 */
public class TemplateSync {

    static final int CHUNK_SIZE = 1024 * 1024;
    private static final long QUIET_MILLIS = 2_000;

    private final Path root;
    /**
     * What each wrapper has, as far as the master knows; only wrappers whose first sync is done are in here.
     */
    private final Map<String, Map<String, Map<String, TemplateFile>>> wrapperStates = new ConcurrentHashMap<>();
    private final Map<String, Object> wrapperLocks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("template-sync").daemon().factory());
    private final Set<String> changedTemplates = ConcurrentHashMap.newKeySet();
    private volatile long lastChange;

    public TemplateSync(Path root) {
        this.root = root;
    }

    /**
     * Creates the global template folders and starts watching {@code templates/} for changes.
     */
    public void start() {
        try {
            for (var global : Templates.GLOBAL) {
                Files.createDirectories(Templates.folder(root, global));
            }
            var watcher = new RecursiveFolderWatcher(root) {
                @Override
                public void onChange(Path path, WatchEvent.Kind<?> kind) {
                    templateOf(path).ifPresent(template -> {
                        changedTemplates.add(template);
                        lastChange = System.currentTimeMillis();
                    });
                }
            };
            var thread = new Thread(watcher::processEvents, "template-watcher");
            thread.setDaemon(true);
            thread.start();
        } catch (IOException e) {
            Console.error("Template changes are not sent automatically: %s", e.getMessage());
        }
        executor.scheduleWithFixedDelay(this::sendChanges, 1, 1, TimeUnit.SECONDS);
    }

    /**
     * Template a changed path belongs to: {@code templates/Lobby/...} is "Lobby", {@code templates/global/server/...}
     * is "global/server".
     */
    private Optional<String> templateOf(Path path) {
        var relative = root.toAbsolutePath().normalize().relativize(path.toAbsolutePath().normalize());
        if (relative.getNameCount() == 0 || relative.toString().isEmpty()) {
            return Optional.empty();
        }
        var first = relative.getName(0).toString();
        if (first.equalsIgnoreCase(Templates.RESERVED_GROUP_NAME)) {
            return relative.getNameCount() >= 2
                    ? Optional.of(first + "/" + relative.getName(1)).filter(Templates.GLOBAL::contains)
                    : Optional.empty();
        }
        return Optional.of(first).filter(this::isSynced);
    }

    /**
     * Global templates and non-static groups; static servers keep their files on their wrapper.
     */
    private boolean isSynced(String template) {
        return Templates.GLOBAL.contains(template) || RexlCloudMaster.getInstance().getServerGroupManager()
                .getServerGroupByName(template).filter(group -> !group.isStaticServer()).isPresent();
    }

    private List<String> syncedTemplates() {
        var templates = new ArrayList<>(Templates.GLOBAL);
        RexlCloudMaster.getInstance().getServerGroupManager().getServerGroups().stream()
                .filter(group -> !group.isStaticServer()).map(ServerGroup::getName).sorted().forEach(templates::add);
        return templates;
    }

    /**
     * Templates the wrappers keep: synced ones and those of static groups, whose jar the wrapper installs itself.
     */
    private Set<String> knownTemplates() {
        var templates = new LinkedHashSet<>(Templates.GLOBAL);
        RexlCloudMaster.getInstance().getServerGroupManager().getServerGroups()
                .forEach(group -> templates.add(group.getName()));
        return templates;
    }

    private void sendChanges() {
        if (changedTemplates.isEmpty() || System.currentTimeMillis() - lastChange < QUIET_MILLIS) {
            return;
        }
        var templates = new ArrayList<>(changedTemplates);
        changedTemplates.removeAll(templates);
        for (var template : templates) {
            if (isSynced(template)) {
                sync(template);
            }
        }
    }

    // Wrappers

    /**
     * Asks a newly connected wrapper for its templates; the sync starts when it answers.
     */
    public void onConnect(Wrapper wrapper) {
        wrapperStates.remove(wrapper.getName());
        wrapper.sendPacket(new PacketContainer(PacketType.TEMPLATE_STATE_REQUEST, new TemplateStateRequestPacket()));
    }

    public void onDisconnect(Wrapper wrapper) {
        wrapperStates.remove(wrapper.getName());
    }

    /**
     * Whether the wrapper got all templates, so servers may start on it.
     */
    public boolean isReady(Wrapper wrapper) {
        return wrapperStates.containsKey(wrapper.getName());
    }

    /**
     * The wrapper reported its templates: sends everything it is missing and removes what the master no longer has.
     * Runs on the calling thread.
     */
    public void onState(Wrapper wrapper, Map<String, Map<String, TemplateFile>> reported) {
        var state = new ConcurrentHashMap<String, Map<String, TemplateFile>>();
        reported.forEach((template, files) -> state.put(template, new ConcurrentHashMap<>(files)));
        synchronized (lockOf(wrapper)) {
            var sent = 0;
            for (var template : syncedTemplates()) {
                sent += syncTo(wrapper, template, state);
            }
            wrapper.sendPacket(new PacketContainer(PacketType.TEMPLATE_DELETE,
                    new TemplateDeletePacket(null, List.of(), knownTemplates())));
            // Not ready before this point, so no server started before its files
            wrapperStates.put(wrapper.getName(), state);
            Console.info("Templates of %s synced (%s files sent)", wrapper.getName(), sent);
        }
    }

    /**
     * Brings the template on all ready wrappers up to date. Returns once everything is on its way.
     */
    public void sync(String template) {
        for (var wrapper : RexlCloudMaster.getInstance().getWrapperManager().getWrappers().values()) {
            var state = wrapperStates.get(wrapper.getName());
            if (state == null) {
                continue;
            }
            synchronized (lockOf(wrapper)) {
                var sent = syncTo(wrapper, template, state);
                if (sent > 0) {
                    Console.info("Sent %s file(s) of %s to %s", sent, template, wrapper.getName());
                }
            }
        }
    }

    /**
     * Asks all wrappers to report their templates again and repairs anything that differs, e.g. files changed by
     * hand on a wrapper.
     */
    public void verifyAll() {
        for (var wrapper : RexlCloudMaster.getInstance().getWrapperManager().getWrappers().values()) {
            if (isReady(wrapper)) {
                wrapper.sendPacket(new PacketContainer(PacketType.TEMPLATE_STATE_REQUEST, new TemplateStateRequestPacket()));
            }
        }
    }

    /**
     * Removes templates of deleted groups from all wrappers.
     */
    public void removeDeletedTemplates() {
        var keep = knownTemplates();
        for (var wrapper : RexlCloudMaster.getInstance().getWrapperManager().getWrappers().values()) {
            var state = wrapperStates.get(wrapper.getName());
            if (state == null) {
                continue;
            }
            synchronized (lockOf(wrapper)) {
                state.keySet().removeIf(template -> !keep.contains(template));
                wrapper.sendPacket(new PacketContainer(PacketType.TEMPLATE_DELETE, new TemplateDeletePacket(null, List.of(), keep)));
            }
        }
    }

    private Object lockOf(Wrapper wrapper) {
        return wrapperLocks.computeIfAbsent(wrapper.getName(), name -> new Object());
    }

    /**
     * @return the number of files sent
     */
    private int syncTo(Wrapper wrapper, String template, Map<String, Map<String, TemplateFile>> state) {
        Map<String, TemplateFile> local;
        try {
            local = Templates.manifest(Templates.folder(root, template));
        } catch (IOException e) {
            Console.error("The template %s could not be read: %s", template, e.getMessage());
            return 0;
        }
        var remote = state.computeIfAbsent(template, name -> new ConcurrentHashMap<>());

        var removed = new ArrayList<String>();
        for (var path : new ArrayList<>(remote.keySet())) {
            if (!local.containsKey(path)) {
                removed.add(path);
            }
        }
        if (!removed.isEmpty()) {
            wrapper.sendPacket(new PacketContainer(PacketType.TEMPLATE_DELETE, new TemplateDeletePacket(template, removed, null)));
            removed.forEach(remote::remove);
        }

        var sent = 0;
        for (var entry : local.entrySet()) {
            if (entry.getValue().equals(remote.get(entry.getKey()))) {
                continue;
            }
            try {
                sendFile(wrapper, template, entry.getKey(), entry.getValue());
                remote.put(entry.getKey(), entry.getValue());
                sent++;
            } catch (IOException | RuntimeException e) {
                // RuntimeException: e.g. the wrapper disconnected; it gets everything on its next connect
                Console.error("%s/%s could not be sent to %s: %s", template, entry.getKey(),
                        wrapper.getName(), e.getMessage());
                if (!wrapper.getChannel().isActive()) {
                    break;
                }
            }
        }
        return sent;
    }

    private void sendFile(Wrapper wrapper, String template, String path, TemplateFile file) throws IOException {
        var key = RexlCloudMaster.getInstance().getWrapperManager().getWrapperKey(wrapper);
        var chunks = (int) Math.max(1, (file.size() + CHUNK_SIZE - 1) / CHUNK_SIZE);
        try (InputStream in = Files.newInputStream(Templates.file(root, template, path))) {
            for (int chunk = 0; chunk < chunks; chunk++) {
                var data = in.readNBytes(CHUNK_SIZE);
                var packet = new TemplateFilePacket(template, path, file.size(), file.sha256(), chunk, chunks,
                        AesUtil.encrypt(data, key));
                // Waits until the chunk is written, so a large file is never in memory as a whole
                wrapper.getChannel().writeAndFlush(new PacketContainer(PacketType.SERVER_TEMPLATE, packet))
                        .syncUninterruptibly();
            }
        }
    }

}
