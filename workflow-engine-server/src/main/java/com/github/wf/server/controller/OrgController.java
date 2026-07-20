package com.github.wf.server.controller;

import com.github.wf.ext.OrgService;
import com.github.wf.ext.OrgTree;
import com.github.wf.ext.OrgUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/org")
@CrossOrigin(origins = "*")
@Tag(name = "Organization", description = "Organization structure — LDAP, Feishu, or DingTalk based org trees, user search, and group listing")
public class OrgController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrgController.class);

    @Autowired(required = false)
    private OrgService orgService;

    @GetMapping("/tree")
    @Operation(summary = "Get org tree", description = "Return the hierarchical organization tree. Returns empty list if no OrgService is configured (LDAP/Feishu/DingTalk).")
    @ApiResponse(responseCode = "200", description = "Organization tree structure",
            content = @Content(schema = @Schema(implementation = OrgTree.class)))
    public List<OrgTree> getTree(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId) {
        if (orgService == null) { log.info("[OrgController] orgService is NULL — LDAP not configured"); return List.of(); }
        log.info("[OrgController] orgService={} — fetching tree...", orgService.getClass().getSimpleName());
        List<OrgTree> tree = orgService.getOrgTree();
        log.info("[OrgController] tree returned {} root nodes", tree.size());
        return tree;
    }

    @GetMapping("/users")
    @Operation(summary = "Search users", description = "Search for organization users by keyword. Returns uid, name, and department for each match.")
    @ApiResponse(responseCode = "200", description = "List of matching users",
            content = @Content(schema = @Schema(implementation = Map.class)))
    public List<Map<String, String>> searchUsers(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId,
            @Parameter(description = "Search keyword (matches uid, name, or department)")
            @RequestParam(value = "q", defaultValue = "") String q) {
        if (orgService == null) return List.of();
        List<OrgUser> users = orgService.searchUsers(q);
        List<Map<String, String>> result = new ArrayList<>();
        for (OrgUser u : users) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("uid", u.getUid());
            m.put("name", u.getCn() != null && !u.getCn().isEmpty() ? u.getCn() : u.getUid());
            m.put("department", u.getDepartment() != null ? u.getDepartment() : "");
            result.add(m);
        }
        return result;
    }

    @GetMapping("/groups")
    @Operation(summary = "List groups", description = "Return all available organization groups (e.g. LDAP groups, departments). Returns empty list if no OrgService is configured.")
    @ApiResponse(responseCode = "200", description = "List of group names")
    public List<String> listGroups(
            @Parameter(description = "User ID for multi-tenant isolation", required = true)
            @RequestHeader("X-User-Id") String userId) {
        if (orgService == null) return List.of();
        return orgService.listGroups();
    }
}
