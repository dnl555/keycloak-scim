package sh.libre.scim.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import sh.libre.scim.jpa.ScimResource;

/**
 * The downstream resource is addressed by the key the downstream assigned, which lives in the
 * stored mapping. Taking it from adapter state instead let the IdP's current attribute reach the
 * request URL, so an IdP changing its identifier pointed our updates at a resource that does not
 * exist there and every push failed.
 */
class ScimClientTargetKeyTest {

    private ScimResource mapping(String externalId) {
        ScimResource r = new ScimResource();
        r.setExternalId(externalId);
        return r;
    }

    @Test
    void addressesTheResourceByTheStoredMapping() {
        assertEquals("JSPARE", ScimClient.targetKey(mapping("JSPARE"), "6382b95c-c666-4f10-88e9"));
    }

    @Test
    void fallsBackWhenTheMappingCarriesNoKey() {
        assertEquals("fallback", ScimClient.targetKey(mapping(null), "fallback"));
        assertEquals("fallback", ScimClient.targetKey(mapping(""), "fallback"));
        assertEquals("fallback", ScimClient.targetKey(null, "fallback"));
    }
}
