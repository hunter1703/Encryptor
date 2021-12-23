package main.enums;

public enum FileType {
    REGULAR("mydat"), EXTERNAL_SYMLINK("extdat"), INTERNAL_SYMLINK("intdat"), NON_REGULAR("");

    private final String extension;

    FileType(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }
}
