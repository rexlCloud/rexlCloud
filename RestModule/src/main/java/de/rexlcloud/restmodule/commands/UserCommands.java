package de.rexlcloud.restmodule.commands;

import de.rexlcloud.core.command.CommandMethod;
import de.rexlcloud.core.console.Console;
import de.rexlcloud.core.console.animation.ConsoleAnimation;
import de.rexlcloud.core.console.animation.questions.StringAnimationQuestion;
import de.rexlcloud.core.console.animation.questions.StringAnimationQuestionBlacklist;
import de.rexlcloud.core.console.animation.questions.StringAnimationQuestionWhitelist;
import de.rexlcloud.restmodule.RestModule;
import de.rexlcloud.restmodule.user.UserManager;

public class UserCommands {

    @CommandMethod("user create")
    public void createUser() {
        var animation = new ConsoleAnimation();
        animation.addQuestion(new StringAnimationQuestionBlacklist("username", "Name of the new User", null,
                RestModule.getInstance().getUserManager().getAllUserNames()));
        animation.addQuestion(new StringAnimationQuestion("password", "Password of the new User"));
        animation.onFinish(e -> {
            var username = e.<String>get("username").get();
            var password = e.<String>get("password").get();
            if (password.length() < UserManager.MIN_PASSWORD_LENGTH) {
                Console.error("[RestModule] Passwords need at least %s characters", UserManager.MIN_PASSWORD_LENGTH);
                return;
            }
            RestModule.getInstance().getUserManager().createUser(username, password).ifPresentOrElse(
                    user -> Console.info("[RestModule] Created user '%s'", username),
                    () -> Console.error("[RestModule] Usernames need 3 to 32 letters, numbers, '-' or '_'"));
        });
        Console.addConsoleAnimation(animation);
    }

    @CommandMethod("user delete")
    public void deleteUser() {
        var animation = new ConsoleAnimation();
        animation.addQuestion(new StringAnimationQuestionWhitelist("username", "Name of the user to Delete",
                RestModule.getInstance().getUserManager().getAllUserNames()));
        animation.onFinish(e -> {
            var username = e.<String>get("username").get();
            RestModule.getInstance().getUserManager().getUser(username).ifPresent(user -> {
                RestModule.getInstance().getUserManager().deleteUser(user);
            });
            Console.info("[RestModule] Deleted user '%s'", username);
        });
        Console.addConsoleAnimation(animation);
    }

}
