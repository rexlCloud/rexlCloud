package de.rexlcloud.master.commands.animations;

import de.rexlcloud.core.console.animation.AnimationCompletion;
import de.rexlcloud.core.console.animation.AnimationQuestion;
import de.rexlcloud.core.server.files.ServerJar;
import de.rexlcloud.master.RexlCloudMaster;

public class ServerInstallTypeAnimationQuestion extends AnimationQuestion<ServerJar> {

    public ServerInstallTypeAnimationQuestion(String key, String question) {
        super(key, question,
                RexlCloudMaster.getInstance().getServerJarManager().getServerJars()
                        .stream()
                        .map(t -> new AnimationCompletion(t.getFullText()))
                        .toArray(AnimationCompletion[]::new)
        );
    }

    @Override
    public boolean canBeAnswered(String input) {
        return RexlCloudMaster.getInstance().getServerJarManager().hasServerJar(input);
    }

    @Override
    public ServerJar parseAnswer(String input) {
        return RexlCloudMaster.getInstance().getServerJarManager().getServerJar(input);
    }

}
