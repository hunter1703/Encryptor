package main.fs.beans;

public class FileInfo {
    private final String name;
    private final String encryptedFilePath;
    private final String symlinkTarget;
    private final Boolean isInternalSymlink;

    public FileInfo(String name, String encryptedFilePath, String symlinkTarget, Boolean isInternalSymlink) {
        this.name = name;
        this.encryptedFilePath = encryptedFilePath;
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
