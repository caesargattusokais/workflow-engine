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
 *   <li>Global ADMIN: create/delete groups, appoint group admins, manage every group.</li>
 *   <li>Group admin: add/remove ordinary members of their own group only.</li>
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
            mine.addAll(lower(actor.getGroups()));
            mine.addAll(lower(actor.getAdminGroups()));
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

    // ── Group lifecycle (global ADMIN only) ──────────────────────────────

    public GroupInfo create(AuthUser actor, String id, String name, String description) {
        requireAdmin(actor);
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
        return info;
    }

    public void delete(AuthUser actor, String groupId) {
        requireAdmin(actor);
        String id = normalize(groupId);
        if (groups.find(id).isEmpty()) throw GroupException.notFound("群不存在: " + id);
        long memberCount = members(actor, id).size();
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

    /** Appoint or revoke a group admin. Global ADMIN only. */
    public void setAdmin(AuthUser actor, String groupId, String username, boolean admin) {
        requireAdmin(actor);
        String id = normalize(groupId);
        String target = normalizeUser(username);
        Stored targetUser = requireUser(target);
        List<String> gs = new ArrayList<>(lower(targetUser.user().getGroups()));
        List<String> ag = new ArrayList<>(lower(targetUser.user().getAdminGroups()));
        if (admin) {
            if (!gs.contains(id)) gs.add(id);
            if (!ag.contains(id)) ag.add(id);
        } else {
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

    private boolean isGroupAdmin(AuthUser actor, String groupId) {
        return isAdmin(actor) || lower(actor.getAdminGroups()).contains(groupId);
    }

    private boolean isMember(AuthUser actor, String groupId) {
        return isGroupAdmin(actor, groupId) || lower(actor.getGroups()).contains(groupId);
    }

    private void requireAuthenticated(AuthUser actor) {
        if (actor == null) throw GroupException.forbidden("请先登录");
    }

    private void requireAdmin(AuthUser actor) {
        requireAuthenticated(actor);
        if (!isAdmin(actor)) throw GroupException.forbidden("只有全局管理员可以执行此操作");
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
