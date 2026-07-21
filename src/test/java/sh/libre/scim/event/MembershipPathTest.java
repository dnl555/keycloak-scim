package sh.libre.scim.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class MembershipPathTest {

    // The Admin REST API (PUT /users/{u}/groups/{g}) writes the membership from the user side.
    @Test
    void parsesAdminRestShape() {
        Optional<MembershipPath> p = MembershipPath.parse(
                "users/20f1e763-e01e-4b7b-bad8-a0ac9a6589d4/groups/736f6959-cf75-4137-a81d-e1d9b67fe6c8");
        assertTrue(p.isPresent());
        assertEquals("20f1e763-e01e-4b7b-bad8-a0ac9a6589d4", p.get().userId());
        assertEquals("736f6959-cf75-4137-a81d-e1d9b67fe6c8", p.get().groupId());
    }

    // A SCIM server patching Group.members writes it from the group side, with the ids in the
    // opposite order. Ignoring this shape means IdP-driven membership never propagates outbound.
    @Test
    void parsesScimShape() {
        Optional<MembershipPath> p = MembershipPath.parse(
                "groups/736f6959-cf75-4137-a81d-e1d9b67fe6c8/members/20f1e763-e01e-4b7b-bad8-a0ac9a6589d4");
        assertTrue(p.isPresent());
        assertEquals("20f1e763-e01e-4b7b-bad8-a0ac9a6589d4", p.get().userId());
        assertEquals("736f6959-cf75-4137-a81d-e1d9b67fe6c8", p.get().groupId());
    }

    // Both shapes must yield the same pair, or the handler pushes the wrong resource.
    @Test
    void bothShapesAgree() {
        MembershipPath admin = MembershipPath.parse("users/u1/groups/g1").orElseThrow();
        MembershipPath scim = MembershipPath.parse("groups/g1/members/u1").orElseThrow();
        assertEquals(admin.userId(), scim.userId());
        assertEquals(admin.groupId(), scim.groupId());
    }

    @Test
    void ignoresUnrelatedPaths() {
        assertTrue(MembershipPath.parse("users/u1").isEmpty());
        assertTrue(MembershipPath.parse("groups/g1").isEmpty());
        assertTrue(MembershipPath.parse("groups/g1/children").isEmpty());
        assertTrue(MembershipPath.parse("users/u1/role-mappings").isEmpty());
        assertTrue(MembershipPath.parse("").isEmpty());
        assertTrue(MembershipPath.parse(null).isEmpty());
    }

    // Ids must not swallow separators, otherwise a longer path silently parses into the wrong pair.
    @Test
    void doesNotSpanExtraSegments() {
        assertTrue(MembershipPath.parse("users/u1/groups/g1/extra").isEmpty());
        assertTrue(MembershipPath.parse("groups/g1/members/u1/extra").isEmpty());
    }
}
