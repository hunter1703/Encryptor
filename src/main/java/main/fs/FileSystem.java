package main.fs;

import com.google.gson.Gson;
import main.Utils.EncryptionUtils;
import main.fs.beans.CommitResult;
import main.fs.beans.Directory;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

import static main.Utils.EncryptionUtils.decrypt;
import static main.Utils.EncryptionUtils.encrypt;

public class FileSystem {
    public static final String DELETED_DIR_NAME = ".deleted";
    private static final Gson GSON = new Gson();
    private final Path rootPath;
    private final byte[] key;
    private Directory root;

    public FileSystem(final Path rootPath, final byte[] key) {
        this.rootPath = rootPath;
        this.key = key;
        this.root = buildFileSystem(rootPath);
        System.out.println("Found : " + root.getTotal() + " files in filesystem");
    }

    public Directory findDir(Path path) {
        final Stack<String> stack = new Stack<>();
        while (path != null) {
            stack.push(path.getFileName().toString());
            path = path.getParent();
        }

        Directory current = root;
        while (!stack.isEmpty()) {
            final String dirName = stack.pop();
            final Directory next = current.getDir(dirName);
            if (next == null) {
                return null;
            }
            current = next;
        }
        return current;
    }

    /*
        original is file path relative to source directory in OS filesystem
        target is file path relative to target directory in OS filesystem
     */
    public boolean addOrUpdateFile(Path original, final Path target, final Path mountPoint) {
        original = mountPoint.resolve(original);
        final Directory parent = createDirIfAbsent(original.getParent());
        return parent.createOrUpdateFile(original.getFileName().toString(), root.getPath().relativize(target).toString());
    }

    /*
        original is file path relative to source directory
        target is file path relative to target directory
     */
    public boolean addOrUpdateSymlinkFile(Path original, final Path target, final Path symlinkTarget, final boolean isInternalSymlink, final Path mountPoint) {
        original = mountPoint.resolve(original);
        return createDirIfAbsent(original.getParent()).createOrUpdateSymlinkFile(original.getFileName().toString(), root.getPath().relativize(target).toString(), symlinkTarget, isInternalSymlink);
    }

    public Directory createDirIfAbsent(Path path) {
        final Stack<String> stack = new Stack<>();
        while (path != null) {
            try {
                stack.push(path.getFileName().toString());
            } catch (Exception ex) {
                System.out.println("path : " + path);
                System.out.println("file : " + path.getFileName());
                throw new RuntimeException(ex);
            }
            path = path.getParent();
        }

        Directory parentDirectory = root;
        while (!stack.isEmpty()) {
            final String dirName = stack.pop();
            parentDirectory = parentDirectory.createDirIfAbsent(dirName);
        }
        return parentDirectory;
    }

    //persist new dir structure in file
    public CommitResult commit() {
        flush();
        final int removed = clean();
        return new CommitResult(removed, 0);
    }

    public boolean removeDir(final Path path) {
        final Directory parent = findDir(path.getParent());
        return parent.removeDir(path.getFileName().toString());
    }

    public boolean removeFile(final Path path) {
        final Directory parent = findDir(path.getParent());

        if (parent == null) {
            return false;
        }
        return parent.removeFile(path.getFileName().toString());
    }

    public void reload() {
        root = buildFileSystem(rootPath);
    }

    public int clean() {
        try {
            final Path rootPath = root.getPath();
            final Set<Path> allFiles = Files.walk(rootPath).filter(path -> !path.toFile().isDirectory())
                    .filter(EncryptionUtils::isValid).map(rootPath::relativize).collect(Collectors.toSet());
            allFiles.removeAll(getReferencedFiles(root));
            //remaining files are orphaned
            for (final Path path : allFiles) {
                //move to trash folder instead
                final Path deletedFilePath = rootPath.resolve(DELETED_DIR_NAME).resolve(path);
                Files.createDirectories(deletedFilePath.getParent());
                Files.move(rootPath.resolve(path), deletedFilePath);
            }
            return allFiles.size();
        } catch (Exception ex) {
            throw new RuntimeException("Error cleaning up the filesystem", ex);
        }
    }

    public Directory getRoot() {
        return root;
    }

    private static Set<Path> getReferencedFiles(final Directory dir) {
        final Set<Path> referencedFiles = dir.getAllFiles().stream().map(f -> Paths.get(f.getEncryptedFilePath())).collect(Collectors.toSet());
        dir.getAllSubDirs().forEach(d -> referencedFiles.addAll(getReferencedFiles(d)));
        return referencedFiles;
    }

    private void flush() {
        try {
            final byte[] data = GSON.toJson(root).getBytes(StandardCharsets.UTF_8);
            encrypt(data, key);

            final FileOutputStream fos = new FileOutputStream(root.getPath().resolve(".fs").toFile());
            fos.write(data);
            fos.flush();
            fos.close();
        } catch (Exception ex) {
            throw new RuntimeException("Error flushing filesystem index", ex);
        }
    }

    private Directory buildFileSystem(final Path path) {
        try {
            final byte[] data = Files.readAllBytes(path.resolve(".fs"));
            decrypt(data, key);
            Directory rootDir = GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(data)), Directory.class);
            rootDir = rootDir == null ? new Directory(path.getFileName().toString(), path) : rootDir;
            rootDir.setPath(path);
            populateDirPaths(rootDir);
            return rootDir;
        } catch (Exception ex) {
            System.out.println("Encountered error while rebuilding filesystem : " + ex);
            throw new RuntimeException(ex);
        }
    }

    private static void populateDirPaths(final Directory dir) {
        final Path path = dir.getPath();

        for (final Directory subdir : dir.getAllSubDirs()) {
            subdir.setPath(path.resolve(subdir.getName()));
            populateDirPaths(subdir);
        }
    }

}
