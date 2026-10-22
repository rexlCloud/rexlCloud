package de.rexlcloud.launcher;

import de.rexlcloud.launcher.dependencies.Updater;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

public class Launcher {

    private static Updater updater;

    public static void main(String[] args) throws Exception {
        updater = new Updater();
        updateSelf();
        var arguments = Arrays.asList(args);
        if (arguments.contains("--master")) {
            new Launcher().startMaster(args);
            return;
        } else if (arguments.contains("--wrapper")) {
            new Launcher().startWrapper(args);
            return;
        }

        System.out.println("                 _   ___   _                   _ ");
        System.out.println("                (_ )(  _ \\(_ )                ( )");
        System.out.println(" _ __   __       | || ( (_)| |   _   _   _   _| |");
        System.out.println("(  __)/ __ \\ \\/ )| || |  _ | | / _ \\( ) ( )/ _  |");
        System.out.println("| |  (  ___/)  ( | || (_( )| |( (_) ) (_) | (_| |");
        System.out.println("(_)   \\____)_/\\_)___)____/(___)\\___/ \\___/ \\__ _)");
        System.out.println("                                                 ");
        System.out.println("What do you want to start? valid options: 'master', 'wrapper'");
        Scanner sc = new Scanner(System.in);
        while (true) {
            var line = sc.nextLine();
            if (line.equalsIgnoreCase("master")) {
                new Launcher().startMaster(args);
                break;
            } else if (line.equalsIgnoreCase("wrapper")) {
                new Launcher().startWrapper(args);
                break;
            } else {
                System.out.println("That is not a valid option: valid options are: 'master', 'wrapper'");
            }
        }
    }

    /**
     * Replaces this launcher with the newest release (used from the next start), so it keeps itself up to date.
     */
    private static void updateSelf() {
        try {
            var ownJar = java.nio.file.Path.of(Launcher.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            updater.updateLauncher(ownJar, Launcher.class.getPackage().getImplementationVersion());
        } catch (java.net.URISyntaxException | RuntimeException e) {
            System.err.println("Could not update the launcher: " + e.getMessage());
        }
    }

    private void startMaster(String[] args) throws Exception {
        var newArgs = Arrays.stream(args).filter(s -> !s.contains("--version")).toList();
        updater.download("master");
        updater.loadMaster(getClass().getClassLoader(), new ArrayList<>(newArgs));
    }

    private void startWrapper(String[] args) throws Exception {
        var newArgs = Arrays.stream(args).filter(s -> !s.contains("--version")).toList();
        updater.download("wrapper");
        updater.loadWrapper(getClass().getClassLoader(), new ArrayList<>(newArgs));
    }

}
