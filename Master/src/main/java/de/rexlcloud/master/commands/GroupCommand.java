package de.rexlcloud.master.commands;

import de.rexlcloud.core.command.Argument;
import de.rexlcloud.core.command.CommandDescription;
import de.rexlcloud.core.command.CommandMethod;
import de.rexlcloud.core.command.Suggestion;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.ConsoleAnimation;
import de.rexlcloud.core.console.animation.questions.BooleanAnimationQuestion;
import de.rexlcloud.core.console.animation.questions.IntegerAnimationQuestion;
import de.rexlcloud.core.console.animation.questions.ServerTypeAnimationQuestion;
import de.rexlcloud.core.console.animation.questions.StringAnimationQuestionBlacklistNoBlanks;
import de.rexlcloud.core.server.ServerGroup;
import de.rexlcloud.core.server.ServerType;
import de.rexlcloud.core.server.files.ServerJar;
import de.rexlcloud.core.server.template.Templates;
import de.rexlcloud.master.RexlCloudMaster;
import de.rexlcloud.master.commands.animations.ServerInstallTypeAnimationQuestion;
import de.rexlcloud.master.commands.animations.WrapperAnimationQuestion;
import de.rexlcloud.master.wrapper.Wrapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroupCommand {

    @CommandMethod("group create")
    @CommandDescription("Create a new ServerGroup")
    public void createGroup() {
        var animation = new ConsoleAnimation();
        var blackListServerNames = new ArrayList<>(RexlCloudMaster.getInstance().getServerGroupManager().getServerGroups()
                .stream().map(ServerGroup::getName).toList());
        // Holds the global templates
        blackListServerNames.add(Templates.RESERVED_GROUP_NAME);
        animation.addQuestion(new StringAnimationQuestionBlacklistNoBlanks("name", "Name of the new group?", null,
                blackListServerNames));
        animation.addQuestion(new ServerTypeAnimationQuestion("type", "Type of the new group?"));
        animation.addQuestion(
                new IntegerAnimationQuestion("memory", "How much memory should each server have?",
                        new AnimationCompletion("512"),
                        new AnimationCompletion("1024")).setMinValue(256));
        var staticQuestion = new BooleanAnimationQuestion("static", "Should this group be static?");
        staticQuestion.onFinishFirst(e -> {
            if (e.get()) {
                animation.addQuestion(
                        new WrapperAnimationQuestion("wrapper",
                                "On which wrapper should the server run?"));
            } else {
                animation.addQuestion(
                        new IntegerAnimationQuestion("servers", "How many servers should be online?")
                                .setMinValue(0));
            }
        });
        animation.addQuestion(staticQuestion);
        animation.addQuestion(
                new BooleanAnimationQuestion("fallback", "Should servers from this group be fallbacks?"));
        animation.addQuestion(
                new ServerInstallTypeAnimationQuestion("install",
                        "What server version should get installed?"));
        animation.onFinish(e -> {
            var name = e.getDirectString("name");
            var type = e.getDirectServerType("type");
            int memory = e.getDirectInt("memory");
            boolean stat = e.getDirectBoolean("static");
            boolean fallback = e.getDirectBoolean("fallback");
            var install = e.<ServerJar>getDirect("install");
            var wrapper = e.<String>get("wrapper").orElse(null);
            int servers = wrapper == null ? e.getDirectInt("servers") : 1;
            // Installing the jar can take minutes (Spigot is built with BuildTools), so the console stays usable
            Thread.ofVirtual().start(() -> {
                try {
                    RexlCloudMaster.getInstance().getServerGroupManager()
                            .createServerGroup(name, type, memory, stat, wrapper, servers, fallback, install);
                    Console.info("Created the group %s", name);
                } catch (RuntimeException ex) {
                    Console.error("Could not create the group %s: %s", name, ex.getMessage());
                }
            });
        });
        Console.addConsoleAnimation(animation);
    }

    @CommandMethod("group edit <name> java <java>")
    public void editJava(@Argument(value = "name", suggestions = "serverGroup") String name,
                         @Argument(value = "java", suggestions = "javaRuntime") String java) {
        var master = RexlCloudMaster.getInstance();
        master.getServerGroupManager().getServerGroupByName(name).ifPresentOrElse(serverGroup -> {
            if (!master.getWrapperManager().isKnownJavaCommand(java)) {
                Console.error("Unknown Java installation %s. Possible: java, %s", java,
                        String.join(", ", master.getWrapperManager().getJavaExecutables()));
                return;
            }
            serverGroup.setJavaCommand(java);
            master.getServerGroupManager().save();
            Console.info("New servers of %s start with %s", name, java);
        }, () -> Console.error("The group %s does not exist!", name));
    }

    @CommandMethod("group edit <name> memory <memory>")
    public void editMemory(@Argument(value = "name", suggestions = "serverGroup") String name,
                           @Argument(value = "memory", suggestions = "memory") int memory) {
        RexlCloudMaster.getInstance().getServerGroupManager().getServerGroupByName(name)
                .ifPresentOrElse(serverGroup -> {
                    if (memory <= 100) {
                        Console.error("A group needs at least 100 MB of memory!");
                        return;
                    }
                    serverGroup.setMemory(memory);
                    RexlCloudMaster.getInstance().getServerGroupManager().save();
                    Console.info("Memory changed to %s MB!", memory);
                }, () -> Console.error("The group %s does not exist!", name));
    }

    @CommandMethod("group edit <name> minServers <minServers>")
    public void editMinServers(@Argument(value = "name", suggestions = "serverGroup") String name,
                               @Argument("minServers") int minServers) {
        RexlCloudMaster.getInstance().getServerGroupManager().getServerGroupByName(name)
                .ifPresentOrElse(serverGroup -> {
                    if (minServers < 0) {
                        Console.error("The minimum servers cannot be below 0!");
                        return;
                    }
                    serverGroup.setMinServers(minServers);
                    RexlCloudMaster.getInstance().getServerGroupManager().save();
                    Console.info("Minimum servers changed to %s!", minServers);
                }, () -> Console.error("The group %s does not exist!", name));
    }

    @CommandMethod("group edit <name> isFallback <fallback>")
    public void editFallback(@Argument(value = "name", suggestions = "serverGroup") String name,
                             @Argument(value = "fallback", suggestions = "boolean") boolean fallback) {
        RexlCloudMaster.getInstance().getServerGroupManager().getServerGroupByName(name)
                .ifPresentOrElse(serverGroup -> {
                    serverGroup.setFallback(fallback);
                    RexlCloudMaster.getInstance().getServerGroupManager().save();
                    Console.info("Fallback changed to %s!", fallback);

                }, () -> Console.error("The group %s does not exist!", name));
    }

    @CommandMethod("group edit <name> removeLogs <logs>")
    public void editRmLogs(@Argument(value = "name", suggestions = "serverGroup") String name,
                           @Argument(value = "logs", suggestions = "boolean") boolean fallback) {
        RexlCloudMaster.getInstance().getServerGroupManager().getServerGroupByName(name)
                .ifPresentOrElse(serverGroup -> {
                    serverGroup.setRemoveOldLogs(fallback);
                    RexlCloudMaster.getInstance().getServerGroupManager().save();
                    if (fallback) {
                        Console.info("Logs are now deleted when a server starts!");
                    } else {
                        Console.info("Logs are no longer deleted when a server starts!");
                    }
                }, () -> Console.error("The group %s does not exist!", name));
    }

    @CommandMethod("group edit <name> maxPlayers <players>")
    public void editMaxPlayers(@Argument(value = "name", suggestions = "serverGroup") String name,
                               @Argument(value = "players", suggestions = "maxPlayers") int players) {
        RexlCloudMaster.getInstance().getServerGroupManager().getServerGroupByName(name)
                .ifPresentOrElse(serverGroup -> {
                    serverGroup.setMaxPlayers(players);
                    RexlCloudMaster.getInstance().getServerGroupManager().save();
                    Console.info("Max players changed to %s", players);
                }, () -> Console.error("The group %s does not exist!", name));
    }

    @CommandMethod("group delete <group>")
    public void deleteGroup(@Argument(value = "group", suggestions = "serverGroup") String group) {
        var serverGroupManager = RexlCloudMaster.getInstance().getServerGroupManager();
        serverGroupManager.getServerGroupByName(group).ifPresentOrElse(serverGroup -> {
            serverGroupManager.removeServerGroup(serverGroup);
            RexlCloudMaster.getInstance().getServerManager().stopServersFromGroup(serverGroup);
        }, () -> Console.error("The group %s does not exist!", group));
    }

    @CommandMethod("group sync template <name>")
    public void updateTemplate(@Argument(value = "name", suggestions = "serverGroupNotStatic") String name) {
        var serverGroupManager = RexlCloudMaster.getInstance().getServerGroupManager();
        serverGroupManager.getServerGroupByName(name).ifPresentOrElse(serverGroup -> {
            RexlCloudMaster.getInstance().getTemplateSync().verifyAll();
            Console.info("The templates are now synced with all wrappers!");
        }, () -> Console.error("The group %s does not exist!", name));
    }

    private void addToList(File file, ArrayList<File> list) {
        if (file.isDirectory() && file.listFiles() != null) {
            Console.debug("file %s is a directory", file.getName());
            for (File listFile : file.listFiles()) {
                addToList(listFile, list);
            }
        } else if (file.isFile()) {
            Console.debug("file %s is not a directory", file.getName());
            list.add(file);
        }
    }

    @Suggestion("boolean")
    public List<String> getBooleanSuggestions() {
        return Arrays.asList("true", "false");
    }

    @Suggestion("serverType")
    public List<String> getServerTypes() {
        return Arrays.stream(ServerType.values()).map(Enum::name).toList();
    }

    @Suggestion("serverGroup")
    public List<String> getServerGroups() {
        return RexlCloudMaster.getInstance().getServerGroupManager().getServerGroups().stream()
                .map(ServerGroup::getName).toList();
    }

    @Suggestion("maxPlayers")
    public List<String> getMaxPlayers() {
        return List.of("20", "100");
    }

    @Suggestion("serverGroupNotStatic")
    public List<String> getNotStaticServerGroups() {
        return RexlCloudMaster.getInstance().getServerGroupManager().getServerGroups().stream()
                .filter(serverGroup -> !serverGroup.isStaticServer()).map(ServerGroup::getName).toList();
    }

    @Suggestion("javaRuntime")
    public List<String> getJavaRuntimes() {
        var runtimes = new ArrayList<>(List.of("java"));
        runtimes.addAll(RexlCloudMaster.getInstance().getWrapperManager().getJavaExecutables());
        return runtimes;
    }

    @Suggestion("memory")
    public List<String> getMemoryExamples() {
        return Arrays.asList("512", "1024");
    }

}
