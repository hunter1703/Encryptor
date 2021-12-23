package main.codec;

import main.Utils.EncryptionUtils;
import main.fs.FileSystem;
import main.fs.beans.Directory;
import main.fs.beans.FileInfo;

import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

public class FileDecryptor {
    private final byte[] key;
    private final Path target;
    private final FileSystem filesystem;
    private final Function<byte[], byte[]> dataTransformer;
    private final ForkJoinPool threadpool;
    private final AtomicInteger counter = new AtomicInteger(0);

    public FileDecryptor(byte[] key, Path root, Path target, Function<byte[], byte[]> dataTransformer, int threads) {
        this.key = key;
        this.target = target;
        this.filesystem = new FileSystem(root, key);
        this.dataTransformer = dataTransformer;
        this.threadpool = new ForkJoinPool(threads, pool -> {
            final ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            worker.setName("decryption" + worker.getPoolIndex());
            return worker;
        }, null, false);
    }

    public void decrypt() {
        final Directory rootDir = filesystem.getRoot();
        threadpool.invoke(new Task(rootDir, rootDir));
        threadpool.shutdown();
    }

    private class Task extends RecursiveAction {

        private final Directory root;
        private final Directory dir;

        private Task(Directory root, Directory dir) {
            this.root = root;
            this.dir = dir;
        }

        @Override
        protected void compute() {
            final List<ForkJoinTask<?>> allTasks = new ArrayList<>();
            try {
                final Path rootPath = root.getPath();
                final Path decryptedDir = target.resolve(rootPath.relativize(dir.getPath()));
                Files.createDirectories(decryptedDir);
                for (final FileInfo fileInfo : dir.getAllFiles()) {
                    final Path encryptedFile = rootPath.resolve(fileInfo.getEncryptedFilePath());
                    final Path decryptedFile = decryptedDir.resolve(fileInfo.getName());
                    if (fileInfo.isRegularFile()) {
                        byte[] data = Files.readAllBytes(encryptedFile);
                        EncryptionUtils.decrypt(data, key);
                        data = dataTransformer.apply(data);
                        Files.createFile(decryptedFile);
                        Files.write(decryptedFile, data);
                    } else if (fileInfo.isInternalSymlinkFile()) {
                        final Path linkedFile = target.resolve(fileInfo.getSymlinkTarget());
                        Files.createSymbolicLink(decryptedFile, linkedFile);
                    } else {
                        Files.createSymbolicLink(decryptedFile, Paths.get(fileInfo.getSymlinkTarget()));
                    }
                    logProgress();
                }

                for (final Directory subdir : dir.getAllSubDirs()) {
                    allTasks.add(threadpool.submit(new Task(root, subdir)));
                }
                allTasks.forEach(task -> {
                    try {
                        task.get();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                });
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        private void logProgress() {
            if ((counter.incrementAndGet()) % 10 == 0) {
                System.out.println("Finished : " + counter.get());
            }
        }
    }
}
