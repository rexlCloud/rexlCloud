package de.rexlcloud.core.network;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class AesUtilTest {

    @Test
    void decryptsWhatWasEncryptedWithTheSameKey() {
        var key = UUID.randomUUID();
        var content = "template file".getBytes(StandardCharsets.UTF_8);
        var encrypted = AesUtil.encrypt(content, key);
        Assertions.assertNotNull(encrypted);
        Assertions.assertArrayEquals(content, AesUtil.decrypt(encrypted, key));
    }

    @Test
    void rejectsOtherKeysAndModifiedContent() {
        var key = UUID.randomUUID();
        var encrypted = AesUtil.encrypt(new byte[]{1, 2, 3}, key);
        Assertions.assertNull(AesUtil.decrypt(encrypted, UUID.randomUUID()));
        encrypted[encrypted.length - 1] ^= 1;
        Assertions.assertNull(AesUtil.decrypt(encrypted, key));
        Assertions.assertNull(AesUtil.decrypt(new byte[3], key));
    }

}
