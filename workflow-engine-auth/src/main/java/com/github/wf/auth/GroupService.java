package com.github.wf.auth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Group administration rules.
 *
 * <ul>
 *   <li>Any signed-in user: create a group and become its first group admin.</li>
 *   <li>Creator or global ADMIN: delete a group once it is empty (no members/data).</li>
 *   <li>Group admin or global ADMIN: add members directly (no consent needed),
 *       remove ordinary members, and appoint/revoke group admins — a group must
 *       always keep at least one admin.</li>
 *   <li>Member: see the group and its members, leave voluntarily.</li>
 * </ul>
 *
 * <p>Membership lives in {@link UserStore}; group metadata lives in
 * {@link GroupStore}. This service keeps the two consistent.
 */
public class GroupService {

    private static final String GLOBAL_ADMIN_ROLE = "ADMIN";
    private static final String ID_PATTERN = "[A-Za-z0-9_.-]{1,64}";

    private final UserStore users;
    private final GroupStore groups;
    private final GroupUsageChecker usage;

    public GroupService(UserStore users, GroupStore groups, GroupUsageChecker usage) {
        this.users = users;
        this.groups = groups;
        this.usage = usage;
    }

    public record GroupView(GroupInfo group, int memberCount, boolean member, boolean groupAdmin) {}
    public record MemberView(String username, String displayName, boolean groupAdmin) {}
    public record CandidateView(String username, String displayName) {}

    /** Accounts available for group membership. Deliberately minimal: no roles or hashes. */
    public List<CandidateView> listCandidates(AuthUser actor) {
        requireAuthenticated(actor);
        return users.listAll().stream()
                .map(u -> new CandidateView(u.getUsername(), u.getDisplayName())).toList();
    }

    // ── Visibility ───────────────────────────────────────────────────────

    public List<GroupView> list(AuthUser actor) {
        requireAuthenticated(actor);
        Set<String> mine = new LinkedHashSet<>();
        if (isAdmin(actor)) {
            for (GroupInfo g : groups.listAll()) mine.add(g.id());
        } else {
            AuthUser current = fresh(actor);
            mine.addAll(lower(current.getGroups()));
            mine.addAll(lower(current.getAdminGroups()));
        }
        Map<String, List<AuthUser>> byGroup = membersByGroup();
        List<GroupView> out = new ArrayList<>();
        for (String id : mine) {
            GroupInfo info = groups.find(id).orElseGet(() ->
                    new GroupInfo(id, id, "", "legacy", 0));
            out.add(new GroupView(info, byGroup.getOrDefault(id, List.of()).size(),
                    isMember(actor, id), isGroupAdmin(actor, id)));
        }
        return out;
    }

    public List<MemberView> members(AuthUser actor, String groupId) {
        String id = normalize(groupId);
        requireVisible(actor, id);
        List<MemberView> out = new ArrayList<>();
        for (AuthUser u : users.listAll()) {
            if (lower(u.getGroups()).contains(id) || lower(u.getAdminGroups()).contains(id)) {
                out.add(new MemberView(u.getUsername(), u.getDisplayName(),
                        lower(u.getAdminGroups()).contains(id)));
            }
        }
        return out;
    }

    // ── Group lifecycle (any signed-in user may create) ──────────────────

    public GroupInfo create(AuthUser actor, String id, String name, String description) {
        requireAuthenticated(actor);
        String normalized = normalize(id);
        if (!normalized.matches(ID_PATTERN)) {
            throw GroupException.badRequest("群 ID 只能包含字母、数字、点、下划线、连字符（1-64 位）");
        }
        if (groups.find(normalized).isPresent()) throw GroupException.conflict("群 ID 已存在: " + normalized);
        GroupInfo info = new GroupInfo(normalized,
                name == null || name.isBlank() ? normalized : name.trim(),
                description == null ? "" : description.trim(),
                actor.getUsername(), System.currentTimeMillis());
        groups.create(info);
        // The creator becomes the first group admin. Membership is read from the
        // store, not the request principal, just like leave() does.
        Stored creator = requireUser(actor.getUsername());
        List<String> gs = new ArrayList<>(lower(creator.user().getGroups()));
        List<String> ag = new ArrayList<>(lower(creator.user().getAdminGroups()));
        if (!gs.contains(normalized)) gs.add(normalized);
        if (!ag.contains(normalized)) ag.add(normalized);
        update(actor.getUsername(), gs, ag);
        return info;
    }

    public void delete(AuthUser actor, String groupId) {
        requireAuthenticated(actor);
        String id = normalize(groupId);
        GroupInfo info = groups.find(id).orElseThrow(() -> GroupException.notFound("群不存在: " + id));
        boolean creator = normalizeUser(info.createdBy()).equals(normalizeUser(actor.getUsername()));
        if (!isAdmin(actor) && !creator) {
            throw GroupException.forbidden("只有建群者或全局管理员可以删除该群");
        }
        long memberCount = memberCount(id);
        if (memberCount > 0) {
            throw GroupException.conflict("群内仍有 " + memberCount + " 名成员，请先移除成员");
        }
        if (usage != null && usage.inUse(id)) {
            throw GroupException.conflict("群内仍有实例、定义或草稿，无法删除");
        }
        groups.delete(id);
    }

    // ── Membership (group admin or global ADMIN) ─────────────────────────

    public void addMember(AuthUser actor, String groupId, String username) {
        String id = normalize(groupId);
        requireGroupAdmin(actor, id);
        String target = normalizeUser(username);
        Stored targetUser = requireUser(target);
        List<String> gs = new ArrayList<>(targetUser.user().getGroups());
        if (lower(gs).contains(id)) return; // idempotent
        gs.add(id);
        update(target, gs, targetUser.user().getAdminGroups());
    }

    public void removeMember(AuthUser actor, String groupId, String username) {
        String id = normalize(groupId);
        requireGroupAdmin(actor, id);
        String target = normalizeUser(username);
        Stored targetUser = requireUser(target);
        // Revoking a group admin is a global-admin operation; a group admin may
        // only remove ordinary members from their own group.
        if (!isAdmin(actor) && lower(targetUser.user().getAdminGroups()).contains(id)) {
            throw GroupException.forbidden("只有全局管理员可以移除群管理员");
        }
        List<String> gs = new ArrayList<>(lower(targetUser.user().getGroups()));
        List<String> ag = new ArrayList<>(lower(targetUser.user().getAdminGroups()));
        gs.remove(id);
        ag.remove(id);
        update(target, gs, ag);
    }

    /** A member may leave at any time — no one can be held in a group. */
    public void leave(AuthUser actor, String groupId) {
        String id = normalize(groupId);
        requireAuthenticated(actor);
        // Membership must be read from the store, not from the request principal:
        // a stale principal would allow a second leave after removal.
        Stored target = requireUser(actor.getUsername());
        boolean inGroup = lower(target.user().getGroups()).contains(id)
                || lower(target.user().getAdminGroups()).contains(id);
        if (!inGroup) throw GroupException.notFound("你不属于该群: " + id);
        List<String> gs = new ArrayList<>(lower(target.user().getGroups()));
        List<String> ag = new ArrayList<>(lower(target.user().getAdminGroups()));
        gs.remove(id);
        ag.remove(id);
        update(actor.getUsername(), gs, ag);
    }

    /** Appoint or revoke a group admin: any admin of this group or the global ADMIN. */
    public void setAdmin(AuthUser actor, String groupId, String username, boolean admin) {
        String id = normalize(groupId);
        requireGroupAdmin(actor, id);
        String target = normalizeUser(username);
        Stored targetUser = requireUser(target);
        List<String> gs = new ArrayList<>(lower(targetUser.user().getGroups()));
        List<String> ag = new ArrayList<>(lower(targetUser.user().getAdminGroups()));
        if (admin) {
            if (!gs.contains(id)) gs.add(id);
            if (!ag.contains(id)) ag.add(id);
        } else {
            if (!ag.contains(id)) return; // not an admin of this group — nothing to revoke
            // Keep at least one group admin so the group never becomes unmanageable.
            // The global ADMIN may bypass this: they can manage every group anyway.
            if (!isAdmin(actor) && adminCount(id) <= 1) {
                throw GroupException.conflict("群内至少保留一名管理员，请先任命其他管理员");
            }
            ag.remove(id); // keeps membership; only the admin privilege is revoked
        }
        update(target, gs, ag);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private record Stored(AuthUser user) {}

    private Stored requireUser(String username) {
        Optional<UserStore.StoredUser> found = users.findByUsername(username);
        if (found.isEmpty()) throw GroupException.notFound("用户不存在: " + username);
        return new Stored(found.get().user());
    }

    private void update(String username, List<String> gs, List<String> ag) {
        if (!users.updateMembership(username, gs, ag)) {
            throw GroupException.notFound("用户不存在: " + username);
        }
    }

    /** Raw member count — no visibility check: the creator may have already left. */
    private long memberCount(String groupId) {
        return users.listAll().stream()
                .filter(u -> lower(u.getGroups()).contains(groupId)
                        || lower(u.getAdminGroups()).contains(groupId))
                .count();
    }

    private Map<String, List<AuthUser>> membersByGroup() {
        Map<String, List<AuthUser>> byGroup = new LinkedHashMap<>();
        for (AuthUser u : users.listAll()) {
            Set<String> ids = new LinkedHashSet<>(lower(u.getGroups()));
            ids.addAll(lower(u.getAdminGroups()));
            for (String id : ids) {
                byGroup.computeIfAbsent(id, k -> new ArrayList<>()).add(u);
            }
        }
        return byGroup;
    }

    private boolean isAdmin(AuthUser actor) {
        return actor.getRoles().stream().anyMatch(r -> GLOBAL_ADMIN_ROLE.equalsIgnoreCase(r));
    }

    private AuthUser fresh(AuthUser actor) {
        if (actor == null) return null;
        return users.findByUsername(actor.getUsername())
                .map(UserStore.StoredUser::user).orElse(actor);
    }

    private boolean isGroupAdmin(AuthUser actor, String groupId) {
        if (isAdmin(actor)) return true;
        // Read membership from the store: the request principal can be a stale
        // snapshot right after a create/add/remove in the same session.
        return lower(fresh(actor).getAdminGroups()).contains(groupId);
    }

    private boolean isMember(AuthUser actor, String groupId) {
        return isGroupAdmin(actor, groupId) || lower(fresh(actor).getGroups()).contains(groupId);
    }

    private void requireAuthenticated(AuthUser actor) {
        if (actor == null) throw GroupException.forbidden("请先登录");
    }

    /** Number of users holding the group-admin privilege for {@code groupId}. */
    private long adminCount(String groupId) {
        return users.listAll().stream()
                .filter(u -> lower(u.getAdminGroups()).contains(groupId))
                .count();
    }

    private void requireGroupAdmin(AuthUser actor, String groupId) {
        requireAuthenticated(actor);
        if (!isGroupAdmin(actor, groupId)) throw GroupException.forbidden("只有群管理员可以管理成员");
    }

    private void requireVisible(AuthUser actor, String groupId) {
        requireAuthenticated(actor);
        if (!isMember(actor, groupId)) throw GroupException.notFound("群不存在或无权访问: " + groupId);
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeUser(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static List<String> lower(List<String> values) {
        if (values == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String v : values) if (v != null && !v.isBlank()) out.add(v.toLowerCase(Locale.ROOT));
        return out;
    }
}
