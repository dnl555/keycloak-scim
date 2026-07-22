package sh.libre.scim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Once the downstream has assigned a key for a resource, that key is how we address it and it
 * must stay put. Building the outgoing externalId from the IdP attribute instead meant that an
 * IdP editing its own identifier turned every later update into an implicit re-key, which the
 * downstream rejects, and the user silently stopped syncing.
 */
class UserAdapterExternalIdTest {

    @Test
    void keepsTheDownstreamKeyOnceAssigned() {
        assertEquals("JSPARE", UserAdapter.resolveExternalId("JSPARE", "6382b95c-c666-4f10-88e9", "local-id"));
    }

    @Test
    void usesTheIdpIdentifierBeforeTheDownstreamAssignsOne() {
        assertEquals("00u2bezw6UDXclQ73356",
                UserAdapter.resolveExternalId(null, "00u2bezw6UDXclQ73356", "local-id"));
        assertEquals("00u2bezw6UDXclQ73356",
                UserAdapter.resolveExternalId("", "00u2bezw6UDXclQ73356", "local-id"));
    }

    @Test
    void fallsBackToTheLocalIdWhenNothingElseIsKnown() {
        assertEquals("local-id", UserAdapter.resolveExternalId(null, null, "local-id"));
        assertEquals("local-id", UserAdapter.resolveExternalId("", "", "local-id"));
    }

    @Test
    void reportsWhenTheIdpIdentifierNoLongerMatchesTheDownstreamKey() {
        assertTrue(UserAdapter.identifierDiverged("JSPARE", "6382b95c-c666-4f10-88e9"));
        assertFalse(UserAdapter.identifierDiverged("JSPARE", "JSPARE"));
        assertFalse(UserAdapter.identifierDiverged("JSPARE", null));
        assertFalse(UserAdapter.identifierDiverged(null, "anything"));
    }
}
