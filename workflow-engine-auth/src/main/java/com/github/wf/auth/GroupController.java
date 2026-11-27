package com.github.wf.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Group administration endpoints, mounted at /api/groups by auto-configuration. */
@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService service;

    public GroupController(GroupService service) {
        this.service = service;
    }

    public record CreateGroupRequest(String id, String name, String description) {}
    public record AddMemberRequest(String username) {}
    public record SetAdminRequest(String username, boolean admin) {}

    @GetMapping
    public List<GroupService.GroupView> list(HttpServletRequest request) {
        return service.list(actor(request));
    }

    @GetMapping("/candidates")
    public List<GroupService.CandidateView> candidates(HttpServletRequest request) {
        return service.listCandidates(actor(request));
    }

    @GetMapping("/{id}/members")
    public List<GroupService.MemberView> members(@PathVariable("id") String id, HttpServletRequest request) {
        return service.members(actor(request), id);
    }

    @PostMapping
    public ResponseEntity<GroupInfo> create(@RequestBody CreateGroupRequest req, HttpServletRequest request) {
        return ResponseEntity.status(201).body(service.create(actor(request), req.id(), req.name(), req.description()));
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable("id") String id, HttpServletRequest request) {
        service.delete(actor(request), id);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/members")
    public Map<String, Object> addMember(@PathVariable("id") String id, @RequestBody AddMemberRequest req,
                                         HttpServletRequest request) {
        service.addMember(actor(request), id, req.username());
        return Map.of("ok", true);
    }

    @DeleteMapping("/{id}/members/{username}")
    public Map<String, Object> removeMember(@PathVariable("id") String id, @PathVariable("username") String username,
                                            HttpServletRequest request) {
        service.removeMember(actor(request), id, username);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/leave")
    public Map<String, Object> leave(@PathVariable("id") String id, HttpServletRequest request) {
        service.leave(actor(request), id);
        return Map.of("ok", true);
    }

    @PutMapping("/{id}/admin")
    public Map<String, Object> setAdmin(@PathVariable("id") String id, @RequestBody SetAdminRequest req,
                                        HttpServletRequest request) {
        service.setAdmin(actor(request), id, req.username(), req.admin());
        return Map.of("ok", true);
    }

    private AuthUser actor(HttpServletRequest request) {
        CurrentUser cu = (CurrentUser) request.getAttribute(CurrentUser.ATTRIBUTE);
        if (cu == null || cu.getUser() == null) throw GroupException.forbidden("请先登录");
        return cu.getUser();
    }

    @ExceptionHandler(GroupException.class)
    public ResponseEntity<Map<String, Object>> handle(GroupException e) {
        return ResponseEntity.status(e.status()).body(Map.of("error", e.getMessage()));
    }
}
