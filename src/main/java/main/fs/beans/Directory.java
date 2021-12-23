package main.fs.beans;

import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;

public class Directory {
    private final String name;
    //dynamically, populating this property when loading from file. So that whole filesystem can be moved anywhere without invalid value for this property
    private transient Path path;
    private final ConcurrentHashMap<String, Directory> dirs;
    private final ConcurrentHashMap<String, FileInfo> files;

    public Directory(String name, Path path) {
        this(name, path, new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    public Directory(String name, Path path, ConcurrentHashMap<String, Directory> dirs, ConcurrentHashMap<String, FileInfo> files) {
        this.name = name;
        this.path = path;
        this.dirs = dirs;
        this.files = files;
    }

    public Directory createDirIfAbsent(final String name) {
        return dirs.computeIfAbsent(name, unused -> new Directory(name, this.path.resolve(name)));
    }

    public boolean createOrUpdateFile(final String name, final String originalPath) {
        return files.putIfAbsent(name, new FileInfo(name, originalPath, null, null)) == null;
    }

    public boolean createOrUpdateSymlinkFile(final String name, final String encryptedFilePath, final Path symlinkTarget, final boolean isInternalSymlink) {
        return files.putIfAbsent(name, new FileInfo(name, encryptedFilePath, symlinkTarget.toString(), isInternalSymlink)) == null;
    }

    public boolean removeDir(final String name) {
        return dirs.remove(name) != null;
    }

    public boolean removeFile(final String name) {
        return files.remove(name) != null;
    }

    public int getTotal() {
        int count = files.size();
        for (final Directory subdirs : dirs.values()) {
            count += subdirs.getTotal();
        }
        return count;
    }

    public Directory getDir(final String name) {
        return dirs.get(name);
    }

    public void print() {
        for (final Directory subdir : dirs.values()) {
            System.out.println("[" + subdir.getName() + "]");
        }
        for (final FileInfo file : files.values()) {
            System.out.println(file.getName());
        }
    }

    public void setPath(Path path) {
        this.path = path;
    }

    public Collection<FileInfo> getAllFiles() {
        return files.values();
    }

    public Collection<Directory> getAllSubDirs() {
        return dirs.values();
    }

    public String getName() {
        return name;
    }

    public Path getPath() {
        return path;
    }

}
