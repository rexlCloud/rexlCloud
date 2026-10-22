package de.rexlcloud.wrapper.server;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.AesUtil;
import de.rexlcloud.core.network.packets.TemplateDeletePacket;
import de.rexlcloud.core.network.packets.TemplateFilePacket;
import de.rexlcloud.core.server.template.TemplateFile;
import de.rexlcloud.core.server.template.Templates;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The templates of this wrapper ({@code servers/templates}), kept equal to the master's.
 * <p>
 * Not thread-safe: the wrapper handles all template packets one after another.
 */
public class TemplateStore {

    private final Path root;
    private final Path transfers;
    private final UUID key;
    private final Map<String, Transfer> running = new HashMap<>();

    /**
     * A file being received: chunks are written to a temporary file outside the templates and moved into place
     * once size and checksum match.
     */
    private static final class Transfer {
        private final Path target;
        private final Path temporary;
        private final OutputStream out;
        private final MessageDigest digest = Templates.newDigest();
        private int nextChunk;
        private long written;

        private Transfer(Path target, Path temporary) throws IOException {
            this.target = target;
            this.temporary = temporary;
            this.out = Files.newOutputStream(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    /**
     * @param root folder of the templates, e.g. {@code servers/templates}
     * @param key  key of this wrapper, the files are encrypted with it
     */
    public TemplateStore(Path root, UUID key) {
        this.root = root;
        this.transfers = root.resolveSibling(".template-transfers");
        this.key = key;
    }

    /**
     * Every template of this wrapper with its files, for the master to compare against.
     */
    public Map<String, Map<String, TemplateFile>> state() throws IOException {
        abortTransfers();
        var state = new TreeMap<String, Map<String, TemplateFile>>();
        for (var global : Templates.GLOBAL) {
            state.put(global, Templates.manifest(Templates.folder(root, global)));
        }
        if (Files.isDirectory(root)) {
            try (var folders = Files.list(root)) {
                for (var folder : folders.filter(Files::isDirectory).toList()) {
                    var name = folder.getFileName().toString();
                    if (Templates.isValidName(name)) {
                        state.put(name, Templates.manifest(folder));
                    }
                }
            }
        }
        return state;
    }

    public void receive(TemplateFilePacket packet) {
        var id = packet.getTemplate() + "/" + packet.getPath();
        try {
            var transfer = running.get(id);
            if (packet.getChunk() == 0) {
                if (transfer != null) {
                    close(transfer, true);
                }
                Files.createDirectories(transfers);
                transfer = new Transfer(Templates.file(root, packet.getTemplate(), packet.getPath()),
                        transfers.resolve(UUID.randomUUID() + ".part"));
                running.put(id, transfer);
            } else if (transfer == null || transfer.nextChunk != packet.getChunk()) {
                throw new IOException("chunk " + packet.getChunk() + " arrived out of order");
            }

            var data = AesUtil.decrypt(packet.getData(), key);
            if (data == null) {
                throw new IOException("the data could not be decrypted with this wrapper's key");
            }
            transfer.out.write(data);
            transfer.digest.update(data);
            transfer.written += data.length;
            transfer.nextChunk++;

            if (transfer.nextChunk == packet.getChunks()) {
                running.remove(id);
                complete(transfer, packet);
            }
        } catch (IOException | IllegalArgumentException e) {
            var transfer = running.remove(id);
            if (transfer != null) {
                close(transfer, true);
            }
            Console.error("The template file %s could not be received: %s", id, e.getMessage());
        }
    }

    private void complete(Transfer transfer, TemplateFilePacket packet) throws IOException {
        transfer.out.close();
        var sha256 = HexFormat.of().formatHex(transfer.digest.digest());
        if (transfer.written != packet.getSize() || !sha256.equals(packet.getSha256())) {
            Files.deleteIfExists(transfer.temporary);
            throw new IOException("size or checksum do not match, the file was not changed");
        }
        Files.createDirectories(transfer.target.getParent());
        try {
            Files.move(transfer.temporary, transfer.target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(transfer.temporary, transfer.target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public void delete(TemplateDeletePacket packet) {
        if (packet.getTemplate() != null) {
            for (var path : packet.getPaths()) {
                try {
                    var file = Templates.file(root, packet.getTemplate(), path);
                    Files.deleteIfExists(file);
                    removeEmptyFolders(file.getParent(), Templates.folder(root, packet.getTemplate()));
                } catch (IOException | IllegalArgumentException e) {
                    Console.error("%s/%s could not be deleted: %s", packet.getTemplate(), path, e.getMessage());
                }
            }
        }
        if (packet.getKeepTemplates() != null) {
            removeTemplatesExcept(packet.getKeepTemplates());
        }
    }

    /**
     * Deletes the templates of groups that no longer exist.
     */
    private void removeTemplatesExcept(Set<String> keep) {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (var folders = Files.list(root)) {
            for (var folder : folders.filter(Files::isDirectory).toList()) {
                var name = folder.getFileName().toString();
                if (Templates.isValidName(name) && !keep.contains(name)) {
                    FileUtils.deleteDirectory(folder.toFile());
                    Console.info("Deleted the template %s, its group no longer exists", name);
                }
            }
        } catch (IOException e) {
            Console.error("Old templates could not be deleted: %s", e.getMessage());
        }
    }

    private static void removeEmptyFolders(Path folder, Path stopAt) throws IOException {
        var stop = stopAt.toAbsolutePath().normalize();
        var current = folder.toAbsolutePath().normalize();
        while (current.startsWith(stop) && !current.equals(stop) && Files.isDirectory(current)) {
            try (var children = Files.list(current)) {
                if (children.findAny().isPresent()) {
                    return;
                }
            }
            Files.delete(current);
            current = current.getParent();
        }
    }

    /**
     * Drops unfinished transfers, e.g. after the connection to the master was lost.
     */
    private void abortTransfers() {
        running.values().forEach(transfer -> close(transfer, true));
        running.clear();
        if (Files.isDirectory(transfers)) {
            try {
                FileUtils.cleanDirectory(transfers.toFile());
            } catch (IOException e) {
                Console.debug("Could not clean %s: %s", transfers, e.getMessage());
            }
        }
    }

    private static void close(Transfer transfer, boolean delete) {
        try {
            transfer.out.close();
            if (delete) {
                Files.deleteIfExists(transfer.temporary);
            }
        } catch (IOException ignored) {
        }
    }

}
