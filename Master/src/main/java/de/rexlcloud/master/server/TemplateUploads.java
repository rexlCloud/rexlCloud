package de.rexlcloud.master.server;

import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.network.PacketContainer;
import de.rexlcloud.core.network.PacketType;
import de.rexlcloud.core.network.packets.ServerSaveTemplatePacket;
import de.rexlcloud.core.network.packets.TemplateUploadPacket;
import de.rexlcloud.core.server.template.Templates;
import de.rexlcloud.master.wrapper.Wrapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

/**
 * Saves the files of a running server into a template variant of its group: the wrapper sends them as a zip in
 * chunks, the master unpacks it into {@code templates/<group>/<variant>/}, replacing files with the same path and
 * keeping the others. The template sync then sends the changes to all wrappers.
 */
public class TemplateUploads {

    public static final Pattern VARIANT = Pattern.compile("[A-Za-z0-9_-]{1,32}");
    private static final long TIMEOUT_MINUTES = 15;

    private final Path templates;
    private final Map<UUID, Upload> uploads = new ConcurrentHashMap<>();

    private static final class Upload {
        final String server;
        final Wrapper wrapper;
        final Path target;
        final Path file;
        final OutputStream out;
        final MessageDigest digest;
        final CompletableFuture<Integer> result = new CompletableFuture<>();
        int nextChunk;

        Upload(String server, Wrapper wrapper, Path target, Path file) throws IOException, NoSuchAlgorithmException {
            this.server = server;
            this.wrapper = wrapper;
            this.target = target;
            this.file = file;
            this.digest = MessageDigest.getInstance("SHA-256");
            this.out = Files.newOutputStream(file);
        }
    }

    public TemplateUploads(Path templates) {
        this.templates = templates;
    }

    /**
     * Asks the server's wrapper for its files.
     *
     * @return completes with the number of saved files, or exceptionally with the reason it failed
     */
    public CompletableFuture<Integer> save(Server server, String variant) {
        if (!VARIANT.matcher(variant).matches()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "A variant name may only contain letters, numbers, '-' and '_'"));
        }
        if (server.getGroup().isStaticServer()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "Static servers keep their own files and have no template"));
        }
        var wrapper = server.getWrapper();
        if (!server.isOnline() || wrapper == null) {
            return CompletableFuture.failedFuture(new IllegalStateException(server.getName() + " is not online"));
        }
        var requestId = UUID.randomUUID();
        Upload upload;
        try {
            var target = Templates.folder(templates, server.getGroup().getName()).resolve(variant);
            Files.createDirectories(target.getParent());
            // Its name keeps the template sync away from it while it is incomplete
            var file = Files.createTempFile(target.getParent(), Templates.TEMPORARY_PREFIX + "upload-", ".zip");
            upload = new Upload(server.getName(), wrapper, target, file);
        } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException e) {
            return CompletableFuture.failedFuture(e);
        }
        uploads.put(requestId, upload);
        upload.result.orTimeout(TIMEOUT_MINUTES, TimeUnit.MINUTES)
                .whenComplete((files, error) -> discard(requestId));
        wrapper.sendPacket(new PacketContainer(PacketType.SERVER_SAVE_TEMPLATE,
                new ServerSaveTemplatePacket(server.getName(), server.getKey(), variant, requestId)));
        return upload.result;
    }

    /**
     * A chunk from a wrapper; uploads the master did not ask that wrapper for are ignored.
     */
    public void receive(Wrapper wrapper, TemplateUploadPacket packet) {
        var upload = uploads.get(packet.getRequestId());
        if (upload == null || !upload.wrapper.getName().equals(wrapper.getName())) {
            return;
        }
        try {
            if (packet.getError() != null) {
                throw new IOException(packet.getError());
            }
            if (packet.getChunk() != upload.nextChunk++) {
                throw new IOException("chunk " + packet.getChunk() + " arrived out of order");
            }
            if (packet.getData() != null) {
                upload.out.write(packet.getData());
                upload.digest.update(packet.getData());
            }
            if (!packet.isLast()) {
                return;
            }
            upload.out.close();
            var sha256 = HexFormat.of().formatHex(upload.digest.digest());
            if (!sha256.equalsIgnoreCase(packet.getSha256())) {
                throw new IOException("the upload is damaged (checksum mismatch)");
            }
            var files = extract(upload.file, upload.target);
            Console.info("Saved %s file(s) of %s into %s", files, upload.server, templates.relativize(upload.target));
            upload.result.complete(files);
        } catch (IOException | RuntimeException e) {
            Console.error("%s could not be saved as a template: %s", upload.server, e.getMessage());
            upload.result.completeExceptionally(e);
        }
    }

    private void discard(UUID requestId) {
        var upload = uploads.remove(requestId);
        if (upload == null) {
            return;
        }
        try {
            upload.out.close();
            Files.deleteIfExists(upload.file);
        } catch (IOException ignored) {
            // A leftover temporary file is skipped by the template sync
        }
    }

    /**
     * Unpacks a zip into {@code target}, replacing files with the same path. Entries that would end up outside
     * {@code target} are refused.
     *
     * @return how many files were written
     */
    static int extract(Path zip, Path target) throws IOException {
        var root = target.toAbsolutePath().normalize();
        var count = 0;
        try (var in = new ZipInputStream(Files.newInputStream(zip))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                var file = root.resolve(entry.getName()).normalize();
                if (!file.startsWith(root) || file.equals(root)) {
                    throw new IOException("the upload contains the path " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(file);
                    continue;
                }
                Files.createDirectories(file.getParent());
                // Written next to the file first, so the template sync never sees half a file
                var temporary = file.resolveSibling(Templates.TEMPORARY_PREFIX + file.getFileName());
                Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                count++;
            }
        }
        return count;
    }

}
