package main.fs;

import com.google.gson.Gson;
import main.fs.beans.CommitResult;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static main.Utils.EncryptionUtils.decrypt;
import static main.Utils.EncryptionUtils.encrypt;

public class FileSystem {
    public static final String DELETED_DIR_NAME = ".deleted";
    private static final Gson GSON = new Gson();
    private final Path root;
    private final byte[] key;
    private final Index index;

    public FileSystem(final Path root, final byte[] key) {
        this.root = root;
        this.key = key;
        this.index = buildFileSystem(getIndexPath());
        System.out.println("Found : " + index.targetVsEntry.size() + " files in filesystem");
    }

    public boolean addOrUpdateFile(final Path original, final Path target) {
        return index.addFile(original, target);
    }

    public CommitResult commit() {
        try {
            int danglingEntries = 0;
            for (final String encryptedFilePath : index.targetVsEntry.keySet()) {
                final Path encryptedFile = root.resolve(Paths.get(encryptedFilePath));
                if (!Files.exists(encryptedFile)) {
                    index.removeFile(encryptedFile);
                    danglingEntries++;
                }
            }

            flush();

            final List<Path> orphanedFiles = Files.walk(root).filter(path -> !path.toFile().isDirectory()).filter(path -> !path.toString().endsWith(".fs"))
                    .filter(path -> !index.targetVsEntry.containsKey(getId(path)))
                    .collect(Collectors.toList());
            for (final Path path : orphanedFiles) {
                //move to trash instead
                final Path deletedDirectory = root.resolve(DELETED_DIR_NAME);
                Files.createDirectories(deletedDirectory);
                Files.move(path, deletedDirectory.resolve(path.getFileName()));
            }
            return new CommitResult(orphanedFiles.size(), danglingEntries);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private void flush() throws IOException {
        final byte[] data = GSON.toJson(index).getBytes(StandardCharsets.UTF_8);
        encrypt(data, key);

        final FileOutputStream fos = new FileOutputStream(getIndexPath().toFile());
        fos.write(data);
        fos.flush();
        fos.close();
    }

    private String getId(Path file) {
        return root.relativize(file).toString();
    }

    private Path getIndexPath() {
        return root.resolve(".fs");
    }

    private Index buildFileSystem(final Path path) {
        try {
            final byte[] data = Files.readAllBytes(path);
            decrypt(data, key);
            final Index loadedIndex = GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(data)), Index.class);
            return loadedIndex == null ? new Index(new ConcurrentHashMap<>()) : new Index(new ConcurrentHashMap<>(loadedIndex.targetVsEntry));
        } catch (Exception ex) {
            System.out.println("Encountered error while rebuilding filesystem : " + ex);
            throw new RuntimeException(ex);
        }
    }

    public String getPath(final Path file) {
        return index.targetVsEntry.get(getId(file)).path;
    }

    private static class Directory {
        private final List<Directory> dirs;
        private final List<FileInfo> files;
    }
    private class Index {
        private final ConcurrentHashMap<String, IndexEntry> targetVsEntry;
        private final ConcurrentHashMap<String, String> originalFileVsEncryptedFile;

        private Index(ConcurrentHashMap<String, IndexEntry> encryptedFileVsEntry) {
            this.targetVsEntry = encryptedFileVsEntry;

            originalFileVsEncryptedFile = new ConcurrentHashMap<>();

            for (final Map.Entry<String, IndexEntry> entry : encryptedFileVsEntry.entrySet()) {
                originalFileVsEncryptedFile.put(entry.getValue().path, entry.getKey());
            }
        }

        public boolean addFile(final Path originalPath, final Path targetPath) {
            final String original = originalPath.toString();
            targetVsEntry.put(getId(targetPath), new IndexEntry(original));
            final String old = originalFileVsEncryptedFile.put(originalPath.toString(), getId(targetPath));
            if (old != null) {
                targetVsEntry.remove(old);
                return false;
            }
            return true;
        }

        public void removeFile(final Path path) {
            targetVsEntry.remove(path.toString());
        }
    }

    private static class IndexEntry {
        private final String path;

        private IndexEntry(String path) {
            this.path = path;
        }
    }
}
