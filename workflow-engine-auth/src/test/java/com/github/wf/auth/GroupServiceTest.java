package com.github.wf.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class GroupServiceTest {

    private final Set<String> usedGroups = new HashSet<>();
    private InMemoryUserStore users;
    private GroupService service;
    private AuthUser admin;
    private AuthUser groupAdmin;
    private AuthUser member;
    private AuthUser outsider;

    @BeforeEach
    void setUp() {
        AuthProperties props = new AuthProperties();
        users = new InMemoryUserStore(props);
        GroupStore groupStore = new InMemoryGroupStore(props);
        service = new GroupService(users, groupStore, usedGroups::contains);

        admin = principal("admin", List.of("ADMIN"), List.of(), List.of());
        groupAdmin = principal("gadmin", List.of("USER"), List.of("g1"), List.of("g1"));
        member = principal("member", List.of("USER"), List.of("g1"), List.of());
        outsider = principal("outsider", List.of("USER"), List.of(), List.of());
        for (AuthUser u : List.of(admin, groupAdmin, member, outsider)) users.put(u, "pass1234");
    }

    private static AuthUser principal(String name, List<String> roles,
                                      List<String> groups, List<String> adminGroups) {
        return new AuthUser(name, name, null, roles, groups, adminGroups, true);
    }

    private static GroupException errorOf(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        return catchThrowableOfType(action, GroupException.class);
    }

    @Test
    void anySignedInUserCanCreateGroupAndBecomesItsAdmin() {
        GroupInfo created = service.create(member, "g2", "Group Two", "desc");
        assertThat(created.id()).isEqualTo("g2");
        assertThat(created.name()).isEqualTo("Group Two");
        assertThat(created.createdBy()).isEqualTo("member");

        AuthUser stored = users.findByUsername("member").orElseThrow().user();
        assertThat(stored.getGroups()).contains("g2");
        assertThat(stored.getAdminGroups()).contains("g2");
        // The creator can manage members immediately, without a global admin.
        service.addMember(member, "g2", "outsider");
        assertThat(users.findByUsername("outsider").orElseThrow().user().getGroups()).contains("g2");
    }

    @Test
    void onlyCreatorOrGlobalAdminCanDeleteGroup() {
        service.create(member, "g3", "G3", "");
        assertThat(errorOf(() -> service.delete(outsider, "g3")).status()).isEqualTo(403);
        assertThat(errorOf(() -> service.delete(groupAdmin, "g3")).status()).isEqualTo(403);

        // The creator must leave first: they are automatically a group admin/member.
        service.leave(member, "g3");
        service.delete(admin, "g3"); // global admin may delete another user's group

        service.create(outsider, "g4", "G4", "");
        service.leave(outsider, "g4");
        service.delete(outsider, "g4"); // creator may delete their own empty group
        assertThat(service.list(outsider)).isEmpty();
    }

    @Test
    void duplicateGroupIdRejected() {
        service.create(admin, "g2", "G2", "");
        GroupException e = errorOf(() -> service.create(admin, "g2", "dup", ""));
        assertThat(e.status()).isEqualTo(409);
        assertThat(e.getMessage()).contains("已存在");
    }

    @Test
    void groupAdminManagesMembersButCannotRemoveAnotherGroupAdmin() {
        // add an ordinary member
        service.addMember(groupAdmin, "g1", "outsider");
        assertThat(users.findByUsername("outsider").orElseThrow().user().getGroups()).contains("g1");
        // idempotent
        service.addMember(groupAdmin, "g1", "outsider");
        assertThat(users.findByUsername("outsider").orElseThrow().user().getGroups()).containsOnly("g1");
        // group admin cannot revoke a group admin
        GroupException e = errorOf(() -> service.removeMember(groupAdmin, "g1", "gadmin"));
        assertThat(e.status()).isEqualTo(403);
        // ... but can remove an ordinary member
        service.removeMember(groupAdmin, "g1", "outsider");
        assertThat(users.findByUsername("outsider").orElseThrow().user().getGroups()).doesNotContain("g1");
    }

    @Test
    void ordinaryMemberCannotManageMembers() {
        GroupException add = errorOf(() -> service.addMember(member, "g1", "outsider"));
        assertThat(add.status()).isEqualTo(403);
        GroupException remove = errorOf(() -> service.removeMember(member, "g1", "gadmin"));
        assertThat(remove.status()).isEqualTo(403);
    }

    @Test
    void memberCanLeaveVoluntarily() {
        service.leave(member, "g1");
        assertThat(users.findByUsername("member").orElseThrow().user().getGroups()).doesNotContain("g1");
        GroupException again = errorOf(() -> service.leave(member, "g1"));
        assertThat(again.status()).isEqualTo(404);
    }

    @Test
    void groupAdminsAndGlobalAdminsAppointAndRevokeAdmins() {
        // ordinary members cannot grant admin rights
        assertThat(errorOf(() -> service.setAdmin(member, "g1", "outsider", true)).status()).isEqualTo(403);

        // a group admin cannot demote themselves while they are the only admin
        GroupException lastAdmin = errorOf(() -> service.setAdmin(groupAdmin, "g1", "gadmin", false));
        assertThat(lastAdmin.status()).isEqualTo(409);
        assertThat(lastAdmin.getMessage()).contains("至少保留一名管理员");

        // a group admin can appoint another member as group admin (auto-joins)
        service.setAdmin(groupAdmin, "g1", "outsider", true);
        AuthUser promoted = users.findByUsername("outsider").orElseThrow().user();
        assertThat(promoted.getAdminGroups()).contains("g1");
        assertThat(promoted.getGroups()).contains("g1");

        // with two admins, self-demotion (退位) is allowed and keeps membership
        service.setAdmin(groupAdmin, "g1", "gadmin", false);
        AuthUser demoted = users.findByUsername("gadmin").orElseThrow().user();
        assertThat(demoted.getAdminGroups()).doesNotContain("g1");
        assertThat(demoted.getGroups()).contains("g1");

        // the global ADMIN may always demote, even the last remaining admin
        service.setAdmin(admin, "g1", "outsider", false);
        assertThat(users.findByUsername("outsider").orElseThrow().user().getAdminGroups()).doesNotContain("g1");
    }

    @Test
    void listingIsScopedToMembership() {
        service.create(admin, "g2", "Group Two", "");
        assertThat(service.list(groupAdmin)).extracting(v -> v.group().id()).containsExactly("g1");
        assertThat(service.list(member)).extracting(v -> v.group().id()).containsExactly("g1");
        assertThat(service.list(outsider)).isEmpty();
        assertThat(service.list(admin)).extracting(v -> v.group().id()).containsExactly("g2");
        assertThat(service.members(member, "g1")).extracting(GroupService.MemberView::username)
                .containsExactlyInAnyOrder("gadmin", "member");
        assertThat(errorOf(() -> service.members(outsider, "g1")).status()).isEqualTo(404);
    }

    @Test
    void deletionRequiresNoMembersAndNoOwnedData() {
        service.create(admin, "g1", "G1", "");
        service.create(admin, "g2", "G2", "");
        GroupException withMembers = errorOf(() -> service.delete(admin, "g1"));
        assertThat(withMembers.status()).isEqualTo(409);
        assertThat(withMembers.getMessage()).contains("成员");

        service.leave(admin, "g2"); // creator auto-joined; empty the group first
        usedGroups.add("g2");
        GroupException withData = errorOf(() -> service.delete(admin, "g2"));
        assertThat(withData.status()).isEqualTo(409);
        assertThat(withData.getMessage()).contains("实例");

        usedGroups.remove("g2");
        service.delete(admin, "g2");
        assertThat(errorOf(() -> service.delete(admin, "g1")).status()).isEqualTo(409);
    }

    @Test
    void unknownTargetUserRejected() {
        assertThat(errorOf(() -> service.addMember(groupAdmin, "g1", "ghost")).status()).isEqualTo(404);
        assertThat(errorOf(() -> service.setAdmin(admin, "g1", "ghost", true)).status()).isEqualTo(404);
    }
}
