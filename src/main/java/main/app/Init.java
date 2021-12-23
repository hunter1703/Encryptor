package main.app;

import main.codec.FileDecryptor;
import main.codec.FileEncryptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Init {
    public static void main(String[] args) throws IOException {
//        args = new String[]{"decrypt", "/Users/gandalf/Downloads/Locked", "/Users/gandalf/Downloads/Unlocked", "justdoit", "7"};
//        args = new String[]{"add", "/Users/gandalf/Downloads/bup", "/Users/gandalf/Downloads/temp", "justdoit", "7"};
        final String operation = args[0];
        if ("init".equals(operation)) {
            final Path targetDir = Paths.get(args[1]).toAbsolutePath();
            final Path filesystem = targetDir.resolve(".fs");
            if (!Files.exists(targetDir) || !Files.exists(filesystem)) {
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                Files.createFile(filesystem);
                System.out.println("Initialization complete");
            } else {
                System.out.println("Directory already exists");
            }
        } else if ("add".equals(operation)) {
            final Path root = Paths.get(args[1]).toAbsolutePath();
            final Path targetDir = Paths.get(args[2]).toAbsolutePath();
            final String password = args[3];
            final int threads = Integer.parseInt(args[4]);
            final FileEncryptor fileEncryptor = new FileEncryptor(password.getBytes(UTF_8), root, targetDir, threads);
            final long total = Files.walk(root).filter(p -> !p.toFile().isDirectory()).count();
            System.out.println("Total files to add : " + total);
            Files.walkFileTree(root, fileEncryptor);
            fileEncryptor.commit();
        } else if ("decrypt".equals(operation)) {
            final Path root = Paths.get(args[1]).toAbsolutePath();
            final Path targetDir = Paths.get(args[2]).toAbsolutePath();
            final String password = args[3];
            final int threads = Integer.parseInt(args[4]);
            final FileDecryptor fileDecryptor = new FileDecryptor(password.getBytes(UTF_8), root, targetDir, Function.identity(), threads);
            Files.walkFileTree(root, fileDecryptor);
            fileDecryptor.await();
        }
    }
}
