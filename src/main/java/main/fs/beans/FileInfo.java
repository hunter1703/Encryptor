package main.fs.beans;

import java.nio.file.Path;

public class FileInfo {
    //original file name
    private final String name;
    //relative encrypted file path in OS file system
    private final String encryptedFilePath;
    private final String symlinkTarget;
    private final Boolean isInternalSymlink;

    public FileInfo(String name, Path encryptedFilePath, String symlinkTarget, Boolean isInternalSymlink) {
        this.name = name;
        this.encryptedFilePath = encryptedFilePath.toString();
        this.symlinkTarget = symlinkTarget;
        this.isInternalSymlink = isInternalSymlink;
    }

    public String getName() {
        return name;
    }

    public String getEncryptedFilePath() {
        return encryptedFilePath;
    }

    public String getSymlinkTarget() {
        return symlinkTarget;
    }

    public boolean isRegularFile() {
        return isInternalSymlink == null;
    }

    public boolean isInternalSymlinkFile() {
        return Boolean.TRUE.equals(isInternalSymlink);
    }

    public boolean isExternalSymlinkFile() {
        return !isRegularFile() && !isInternalSymlinkFile();
    }
}
