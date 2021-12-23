package main.app;

import main.fs.FileSystem;
import main.fs.beans.Directory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

public class Console {

    private final FileSystem fileSystem;

    public Console(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void start() {
        final Scanner scanner = new Scanner(System.in);
        Directory current = fileSystem.getRoot();
        Directory prev = current;
        final Path rootPath = fileSystem.getRoot().getPath();
        boolean exit = false;
        while (!exit) {
            final String command = scanner.next();
            switch (command) {
                case "exit":
                    System.out.println("Bye!");
                    exit = true;
                    break;
                case "pwd":
                    System.out.println("You are at : " + rootPath.relativize(current.getPath()));
                    break;
                case "ls":
                    current.print();
                    break;
                case "mkdir":
                    Path path = rootPath.relativize(current.getPath().resolve(Paths.get(scanner.next())));
                    final Directory newDir = fileSystem.createDirIfAbsent(path);
                    fileSystem.commit();
                    System.out.println("Created directory " + rootPath.relativize(newDir.getPath()));
                    break;
                case "rm":
                    String next = scanner.next();
                    if ("-r".equals(next)) {
                        path = rootPath.relativize(current.getPath().resolve(Paths.get(scanner.next())));
                        final boolean removed = fileSystem.removeDir(path);
                        if (!removed) {
                            System.out.println("No directory exists at path : " + path);
                        } else {
                            fileSystem.commit();
                            System.out.println("Removed directory at path : " + path);
                        }
                    } else {
                        path = rootPath.relativize(current.getPath().resolve(Paths.get(next)));
                        final boolean removed = fileSystem.removeFile(path);
                        if (!removed) {
                            System.out.println("No file exists at path : " + path);
                        } else {
                            fileSystem.commit();
                            System.out.println("Removed file at path : " + path);
                        }
                    }
                    break;
                case "clean":
                    System.out.println("Removed : " + fileSystem.clean() + " orphan files");
                    fileSystem.commit();
                    break;
                default:
                    if (command.startsWith("cd")) {
                        final String destination = scanner.next();
                        switch (destination) {
                            case "-":
                                final Directory temp = prev;
                                prev = current;
                                current = temp;
                                System.out.println("You are at : " + rootPath.relativize(current.getPath()));
                                break;
                            case "..":
                                if (current == fileSystem.getRoot()) {
                                    System.out.println("You are already at the root");
                                } else {
                                    final Directory destinationDir = fileSystem.findDir(rootPath.relativize(current.getPath().getParent()));
                                    prev = current;
                                    current = destinationDir;
                                    System.out.println("You are at : " + rootPath.relativize(current.getPath()));
                                }
                                break;
                            case ".":
                                break;
                            default:
                                final Path destinationPath = current.getPath().resolve(destination);
                                final Directory destinationDir = fileSystem.findDir(rootPath.relativize(destinationPath));
                                if (destinationDir == null) {
                                    System.out.println("Destination : " + rootPath.relativize(destinationPath) + " does not exist");
                                } else {
                                    prev = current;
                                    current = destinationDir;
                                    System.out.println("You are at : " + rootPath.relativize(current.getPath()));
                                }
                        }
                    }
            }
        }
    }
}
