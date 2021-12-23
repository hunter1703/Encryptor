package main.codec;

import main.enums.FileType;
import main.fs.FileSystem;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static main.Utils.EncryptionUtils.*;
import static main.fs.FileSystem.DELETED_DIR_NAME;

public class FileDecryptor extends SimpleFileVisitor<Path> {
    private final byte[] key;
    private final Path target;
    private final main.fs.FileSystem filesystem;
    private final Function<byte[], byte[]> dataTransformer;
    private final ExecutorService executorService;
    final List<Future<?>> allFutures = new ArrayList<>();

    public FileDecryptor(byte[] key, Path root, Path target, Function<byte[], byte[]> dataTransformer, int threads) {
        this.key = key;
        this.target = target;
        this.filesystem = new FileSystem(root, key);
        this.dataTransformer = dataTransformer;
        this.executorService = new MyExecutorServiceBuilder(threads, "decryption").build();
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
        if (dir.getFileName().toString().equals(DELETED_DIR_NAME)) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        allFutures.add(executorService.submit(() -> {
            final Thread currentThread = Thread.currentThread();
            final String threadName = currentThread.getName();
            currentThread.setName(threadName + "_" + file.toString());
            try {
                if (!isValid(file)) {
                    return;
                }
                final String extension = readExtension(file);
                byte[] data = Files.readAllBytes(file);
                decrypt(data, key);

                final Path relativePath = Paths.get(filesystem.getPath(file));
                final Path targetFile = target.resolve(relativePath);

                Files.createDirectories(targetFile.getParent());
                if (FileType.INTERNAL_SYMLINK.getExtension().equals(extension)) {
                    final Path linkedFile = target.resolve(Paths.get(new String(data, StandardCharsets.UTF_8)));
                    Files.createSymbolicLink(targetFile, linkedFile);
                } else if (FileType.EXTERNAL_SYMLINK.getExtension().equals(extension)) {
                    final Path linkedFile = Paths.get(new String(data, StandardCharsets.UTF_8));
                    Files.createSymbolicLink(targetFile, linkedFile);
                } else if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    data = dataTransformer.apply(data);
                    Files.createFile(targetFile);
                    Files.write(targetFile, data);
                } else {
                    System.out.println("Found non-regular file : " + file);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            } finally {
                currentThread.setName(threadName);
            }
        }));
        return FileVisitResult.CONTINUE;
    }

    public void await() {
        final AtomicInteger counter = new AtomicInteger(0);
        allFutures.forEach(f -> {
            try {
                f.get();
                if ((counter.incrementAndGet()) % 10 == 0) {
                    System.out.println("Finished : " + counter.get());
                }
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        });
        System.out.println(counter.get() + " files were decrypted");
        executorService.shutdown();
    }
}
