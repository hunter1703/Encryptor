package main.Utils;

import main.enums.FileType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static main.enums.FileType.*;

public class EncryptionUtils {

    private static final Set<String> VALID_EXTENSIONS = new HashSet<>();

    static {
        for (final FileType fileType : FileType.values()) {
            if (fileType == NON_REGULAR) {
                continue;
            }
            VALID_EXTENSIONS.add(fileType.getExtension());
        }
    }

    private EncryptionUtils() {
    }

    public static void encrypt(final byte[] data, final byte[] key) {
        int i = 0;
        int len = key.length;
        final int n = data.length;
        for (int j = 0; j < n; j++) {
            data[j] = (byte) (data[j] ^ key[i]);
            i = (i + 1) % len;
        }
    }

    public static void decrypt(final byte[] data, final byte[] key) {
        encrypt(data, key);
    }

    public static String getRandomName(final FileType fileType) {
        if (fileType == NON_REGULAR) {
            throw new IllegalArgumentException("Operating no permiited for non-regular files");
        }
        return UUID.randomUUID().toString().replaceAll("-", "") + "." + fileType.getExtension();
    }

    public static FileType getFileType(final Path filePath, final Path root) throws IOException {
        if (Files.isSymbolicLink(filePath)) {
            Path linked = Files.readSymbolicLink(filePath).toAbsolutePath();
            return linked.startsWith(root) ? INTERNAL_SYMLINK : EXTERNAL_SYMLINK;
        } else if (Files.isRegularFile(filePath)) {
            return REGULAR;
        }
        return NON_REGULAR;

    }

    public static boolean isValid(final Path path) {
        final String extension = readExtension(path);
        return VALID_EXTENSIONS.contains(extension);
    }

    public static String readExtension(Path path) {
        final String[] split = path.getFileName().toString().split("\\.");
        return split[split.length - 1];
    }
}
