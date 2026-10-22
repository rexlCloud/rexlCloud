package de.rexlcloud.master.wrapper;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class WrapperCredentials {

    private String name;
    private String address;
    private UUID key;

    public boolean checkCredentials(String address, UUID key) {
        return (this.address.equals(address) || address.equals("127.0.0.1") || address.equals("localhost")) && this.key.equals(key);
    }

}
