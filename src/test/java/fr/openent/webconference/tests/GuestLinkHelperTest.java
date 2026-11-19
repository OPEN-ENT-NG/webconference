package fr.openent.webconference.tests;

import fr.openent.webConference.helper.GuestLinkHelper;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GuestLinkHelperTest {
    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    @Test
    public void nameIsTrimmed() {
        assertEquals("Jean Dupont", GuestLinkHelper.sanitizeName("  Jean Dupont  "));
    }

    @Test
    public void controlCharactersAreRemoved() {
        assertEquals("JeanDupont", GuestLinkHelper.sanitizeName("Jean\u0000\nDupont"));
    }

    @Test
    public void emptyOrBlankNameIsRefused() {
        assertNull(GuestLinkHelper.sanitizeName(null));
        assertNull(GuestLinkHelper.sanitizeName(""));
        assertNull(GuestLinkHelper.sanitizeName("   \t"));
    }

    @Test
    public void nameIsLimitedTo64Characters() {
        assertEquals(repeat('a', 64), GuestLinkHelper.sanitizeName(repeat('a', 64)));
        assertNull(GuestLinkHelper.sanitizeName(repeat('a', 65)));
    }

    @Test
    public void tokenMustBe43Base64UrlCharacters() {
        assertTrue(GuestLinkHelper.isValidToken(repeat('a', 40) + "_-9"));
        assertFalse(GuestLinkHelper.isValidToken(null));
        assertFalse(GuestLinkHelper.isValidToken(repeat('a', 42)));
        assertFalse(GuestLinkHelper.isValidToken(repeat('a', 44)));
        assertFalse(GuestLinkHelper.isValidToken(repeat('a', 42) + "/"));
    }
}
