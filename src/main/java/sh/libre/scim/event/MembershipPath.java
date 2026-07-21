package sh.libre.scim.event;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The user/group pair carried by a GROUP_MEMBERSHIP admin event.
 *
 * Keycloak records two different resource paths for the same fact, depending on which side wrote
 * it. The Admin REST API writes from the user side, a SCIM server patching Group.members writes
 * from the group side, and the two put the ids in opposite positions:
 *
 *   users/{userId}/groups/{groupId}
 *   groups/{groupId}/members/{userId}
 */
public final class MembershipPath {

    private static final Pattern PATTERN = Pattern.compile(
            "^users/(?<userFirst>[^/]+)/groups/(?<groupSecond>[^/]+)$"
                    + "|^groups/(?<groupFirst>[^/]+)/members/(?<userSecond>[^/]+)$");

    private final String userId;
    private final String groupId;

    private MembershipPath(String userId, String groupId) {
        this.userId = userId;
        this.groupId = groupId;
    }

    public static Optional<MembershipPath> parse(String resourcePath) {
        if (resourcePath == null) {
            return Optional.empty();
        }
        Matcher m = PATTERN.matcher(resourcePath);
        if (!m.matches()) {
            return Optional.empty();
        }
        String user = m.group("userFirst") != null ? m.group("userFirst") : m.group("userSecond");
        String group = m.group("groupFirst") != null ? m.group("groupFirst") : m.group("groupSecond");
        return Optional.of(new MembershipPath(user, group));
    }

    public String userId() {
        return userId;
    }

    public String groupId() {
        return groupId;
    }
}
