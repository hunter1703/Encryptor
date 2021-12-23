package main.codec;

import main.enums.FileType;
import main.fs.FileSystem;
import main.fs.beans.CommitResult;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static main.Utils.EncryptionUtils.*;
import static main.enums.FileType.*;

public class FileEncryptor extends SimpleFileVisitor<Path> {
    private final byte[] key;
    private final Path root;
    private final Path target;
    private final FileSystem filesystem;
    private final ExecutorService executorService;
    private final List<Future<EncryptionStatus>> allFutures = new ArrayList<>();


    public FileEncryptor(byte[] key, Path root, Path target, int threads) {
        this.key = key;
        this.root = root;
        this.target = target;
        this.filesystem = new FileSystem(target, key);
        this.executorService = new MyExecutorServiceBuilder(threads, "encryption").build();
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        allFutures.add(executorService.submit(() -> {
            final Thread currentThread = Thread.currentThread();
            final String threadName = currentThread.getName();
            currentThread.setName(threadName + "_" + file.toString());
            try {
                final FileType fileType = getFileType(file, root);
                if (fileType == NON_REGULAR) {
                    System.out.println("Found non-regular file : " + file);
                    return EncryptionStatus.NOOP;
                }
                String name = getRandomName(fileType);
                //to make directory tree shallow
                String directoryName = name.substring(0, 2);
                Path targetPath = target.resolve(directoryName).resolve(name.substring(2));
                while (Files.exists(targetPath)) {
                    name = getRandomName(fileType);
                    directoryName = name.substring(0, 2);
                    targetPath = target.resolve(directoryName).resolve(name.substring(2));
                }
                Files.createDirectories(targetPath.getParent());
                Files.createFile(targetPath);

                byte[] data;
                if (fileType == REGULAR) {
                    data = Files.readAllBytes(file);
                } else {
                    //if target file for symlink does not exists, file.toRealPath() throws exception
                    if (Files.notExists(file)) {
                        return EncryptionStatus.FILE_NOT_EXISTS;
                    }
                    Path linked = file.toRealPath().toAbsolutePath();

                    if (linked.startsWith(root)) {
                        linked = root.relativize(linked);
                    }
                    data = linked.toString().getBytes(StandardCharsets.UTF_8);
                }
                encrypt(data, key);
                Files.write(targetPath, data);
                final boolean newFile = filesystem.addOrUpdateFile(root.getParent().relativize(file), targetPath);
                return newFile ? EncryptionStatus.ADD : EncryptionStatus.UPDATE;
            } catch (Exception ex) {
                throw new RuntimeException("Failed for : " + file, ex);
            } finally {
                currentThread.setName(threadName);
            }
        }));
        return FileVisitResult.CONTINUE;
    }

    public void commit() {
        final AtomicInteger added = new AtomicInteger(0);
        final AtomicInteger updated = new AtomicInteger(0);
        final AtomicInteger noop = new AtomicInteger(0);
        final AtomicInteger notExists = new AtomicInteger(0);
        final AtomicInteger counter = new AtomicInteger(0);
        allFutures.forEach(f -> {
            try {
                final EncryptionStatus status = f.get();
                switch (status) {
                    case NOOP:
                        noop.incrementAndGet();
                        break;
                    case ADD:
                        added.incrementAndGet();
                        break;
                    case UPDATE:
                        updated.incrementAndGet();
                        break;
                    case FILE_NOT_EXISTS:
                        notExists.incrementAndGet();
                        break;
                }
                if ((counter.incrementAndGet()) % 10 == 0) {
                    System.out.println("Finished : " + counter.get());
                }
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println(counter.get() + " files are precessed");
        final CommitResult result = filesystem.commit();
        System.out.println("NoOp : " + noop);
        System.out.println("Added : " + added);
        System.out.println("Updated : " + updated);
        System.out.println("Non-existing files : " + notExists);
        System.out.println("Deleted : " + result.getOrphaned() + " orphaned files from the filesystem");
        System.out.println("Updated : " + result.getDangling() + " dangling entries in the filesystem");
        executorService.shutdown();
    }

    private enum EncryptionStatus {
        NOOP, ADD, UPDATE, FILE_NOT_EXISTS
    }
}
