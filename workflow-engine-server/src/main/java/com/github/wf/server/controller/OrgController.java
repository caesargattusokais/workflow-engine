package com.github.wf.server.controller;

import com.github.wf.ext.OrgService;
import com.github.wf.ext.OrgTree;
import com.github.wf.ext.OrgUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/org")
@CrossOrigin(origins = "*")
public class OrgController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrgController.class);

    @Autowired(required = false)
    private OrgService orgService;

    @GetMapping("/tree")
    public List<OrgTree> getTree() {
        if (orgService == null) { log.info("[OrgController] orgService is NULL — LDAP not configured"); return List.of(); }
        log.info("[OrgController] orgService={} — fetching tree...", orgService.getClass().getSimpleName());
        List<OrgTree> tree = orgService.getOrgTree();
        log.info("[OrgController] tree returned {} root nodes", tree.size());
        return tree;
    }

    @GetMapping("/users")
    public List<Map<String, String>> searchUsers(@RequestParam(value = "q", defaultValue = "") String q) {
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
    public List<String> listGroups() {
        if (orgService == null) return List.of();
        return orgService.listGroups();
    }
}
