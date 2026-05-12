package sh.libre.scim.core;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.persistence.NoResultException;

import de.captaingoldfish.scim.sdk.client.ScimRequestBuilder;
import de.captaingoldfish.scim.sdk.client.builder.PatchBuilder;
import de.captaingoldfish.scim.sdk.common.constants.enums.PatchOp;
import de.captaingoldfish.scim.sdk.common.resources.Group;
import de.captaingoldfish.scim.sdk.common.resources.multicomplex.Member;
import de.captaingoldfish.scim.sdk.common.resources.complex.Meta;
import org.apache.commons.lang3.StringUtils;
import org.jboss.logging.Logger;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;

public class GroupAdapter extends Adapter<GroupModel, Group> {

    private String displayName;
    private Set<String> members = new HashSet<String>();
    // Keycloak user id -> username. Populated in apply(GroupModel) so that
    // outgoing Group payloads can include each member's "display" without
    // re-querying the user model from inside toSCIM/toPatchBuilder.
    private Map<String, String> memberDisplays = new HashMap<>();

    public GroupAdapter(KeycloakSession session, String componentId) {
        super(session, componentId, "Group", Logger.getLogger(GroupAdapter.class));
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        if (this.displayName == null) {
            this.displayName = displayName;
        }
    }

    @Override
    public Class<Group> getResourceClass() {
        return Group.class;
    }

    @Override
    public void apply(GroupModel group) {
        setId(group.getId());
        setDisplayName(group.getName());
        this.memberDisplays = session.users()
                .getGroupMembersStream(session.getContext().getRealm(), group)
                .collect(Collectors.toMap(
                        x -> x.getId(),
                        x -> x.getUsername() == null ? "" : x.getUsername(),
                        (a, b) -> a));
        this.members = this.memberDisplays.keySet();
        this.skip = StringUtils.equals(group.getFirstAttribute("scim-skip"), "true");
    }

    @Override
    public void apply(Group group) {
        setExternalId(group.getId().get());
        setDisplayName(group.getDisplayName().get());
        var groupMembers = group.getMembers();
        if (groupMembers != null && groupMembers.size() > 0) {
            this.members = new HashSet<String>();
            for (var groupMember : groupMembers) {
                var userMapping = this.query("findByExternalId", groupMember.getValue().get(), "User")
                        .getSingleResult();
                this.members.add(userMapping.getId());
            }
        }
    }

    @Override
    public Group toSCIM(Boolean addMeta) {
        var group = new Group();
        group.setId(externalId);
        group.setExternalId(id);
        group.setDisplayName(displayName);
        if (members.size() > 0) {
            var groupMembers = new ArrayList<Member>();
            for (var member : members) {
                var groupMember = new Member();
                try {
                    var userMapping = this.query("findById", member, "User").getSingleResult();
                    groupMember.setValue(userMapping.getExternalId());
                    var ref = new URI(String.format("Users/%s", userMapping.getExternalId()));
                    groupMember.setRef(ref.toString());
                    // Populate "display" with the Keycloak username and "type"
                    // with "User" so the outgoing member object matches the
                    // shape emitted by other Keycloak SCIM plugins (e.g.
                    // scim-for-keycloak). Service Providers commonly surface
                    // "display" in their UI / membership listings.
                    groupMember.setType("User");
                    var display = memberDisplays.get(member);
                    if (display != null && !display.isEmpty()) {
                        groupMember.setDisplay(display);
                    }
                    groupMembers.add(groupMember);
                } catch (Exception e) {
                    LOGGER.error(e);
                }
            }
            group.setMembers(groupMembers);
        }
        if (addMeta) {
            var meta = new Meta();
            try {
                var uri = new URI("Groups/" + externalId);
                meta.setLocation(uri.toString());
            } catch (URISyntaxException e) {
            }
            group.setMeta(meta);
        }
        return group;
    }

    @Override
    public Boolean entityExists() {
        if (this.id == null) {
            return false;
        }
        var group = session.groups().getGroupById(realm, id);
        if (group != null) {
            return true;
        }
        return false;
    }

    @Override
    public Boolean tryToMap() {
        var group = session.groups().getGroupsStream(realm).filter(x -> x.getName() == displayName).findFirst();
        if (group.isPresent()) {
            setId(group.get().getId());
            return true;
        }
        return false;
    }

    @Override
    public void createEntity() {
        var group = session.groups().createGroup(realm, displayName);
        this.id = group.getId();
        for (String mId : members) {
            try {
                var user = session.users().getUserById(realm, mId);
                if (user == null) {
                    throw new NoResultException();
                }
                user.joinGroup(group);
            } catch (Exception e) {
                LOGGER.warn(e);
            }
        }
    }

    @Override
    public Stream<GroupModel> getResourceStream() {
        return this.session.groups().getGroupsStream(this.session.getContext().getRealm());
    }

    @Override
    public Boolean skipRefresh() {
        return false;
    }

    @Override
    public PatchBuilder<Group> toPatchBuilder(ScimRequestBuilder scimRequestBuilder, String url) {
        List<Member> groupMembers = new ArrayList<>();
        PatchBuilder<Group> patchBuilder;
        patchBuilder = scimRequestBuilder.patch(url, Group.class);
        if (members.size() > 0) {
            for (String member : members) {
                var userMapping = this.query("findById", member, "User").getSingleResult();
                var memberBuilder = Member.builder()
                        .value(userMapping.getExternalId())
                        .type("User");
                var display = memberDisplays.get(member);
                if (display != null && !display.isEmpty()) {
                    memberBuilder.display(display);
                }
                groupMembers.add(memberBuilder.build());
            }
            // Note: we intentionally do not emit a PatchOp on path "externalId".
            // Per SCIM 2.0 §3.5.2 / §7 the "externalId" attribute is read-only
            // from the service provider's perspective and MUST be carried only on
            // create or full-replace (PUT). Including it as a PatchOp in a
            // multi-op PATCH triggers undefined behaviour in some Service
            // Providers (notably implementations built on the captaingoldfish
            // scim-sdk, where presence of the externalId op silently aborts
            // sibling ops on "members"). Carrying displayName + members is
            // sufficient for downstream replication.
            patchBuilder.addOperation()
                .path("members")
                .op(PatchOp.REPLACE)
                .valueNodes(groupMembers)
                .next()
                .op(PatchOp.REPLACE)
                .path("displayName")
                .value(displayName)
                .build();
        } else {
            patchBuilder.addOperation()
                .path("members")
                .op(PatchOp.REMOVE)
                .value(null)
                .next()
                .op(PatchOp.REPLACE)
                .path("displayName")
                .value(displayName)
                .build();

            }
        LOGGER.info(patchBuilder.getResource());
        return patchBuilder;
    }
}
