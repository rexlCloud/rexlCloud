package de.rexlcloud.core.api;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CloudTextTest {

    @Test
    void miniMessageBecomesAColouredComponent() {
        var json = JsonParser.parseString(CloudText.toJson("<gold>Hello <bold>world")).getAsJsonObject();
        assertEquals("gold", json.get("color").getAsString());
        assertTrue(json.toString().contains("\"bold\":true"), json.toString());
        assertFalse(json.toString().contains("<gold>"), "tags are applied, not shown");
    }

    @Test
    void classicColourCodesWork() {
        assertEquals("<#55ff55>green <#ff5555>red & more", CloudText.replaceColorCodes("&agreen §cred & more"));
        assertTrue(CloudText.toJson("&agreen").contains("#55FF55") || CloudText.toJson("&agreen").contains("green"));
    }

    @Test
    void plainTextStaysPlain() {
        // A plain component is written as a JSON string
        assertEquals("Hello", JsonParser.parseString(CloudText.toJson("Hello")).getAsString());
    }

}
