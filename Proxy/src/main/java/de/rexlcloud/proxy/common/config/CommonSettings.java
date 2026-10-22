package de.rexlcloud.proxy.common.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@NoArgsConstructor
public class CommonSettings {

    @Getter
    private static final CommonSettings instance = new CommonSettings();

    @Setter
    private boolean maintenance;

}
