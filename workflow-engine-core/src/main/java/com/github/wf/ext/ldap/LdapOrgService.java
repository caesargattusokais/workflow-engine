package com.github.wf.ext.ldap;

import com.github.wf.ext.OrgService;
import com.github.wf.ext.OrgUser;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import java.util.*;

/**
 * LDAP/AD organization service using JNDI (built-in, no extra dependencies).
 *
 * Config keys:
 *   ldap.url          = ldap://dc.company.com:389
 *   ldap.base         = dc=company,dc=com
 *   ldap.user         = cn=admin,dc=company,dc=com
 *   ldap.password     = secret
 *   ldap.userFilter   = (&(objectClass=user)(sAMAccountName={0}))
 *   ldap.groupFilter  = (&(objectClass=group)(member={0}))
 *   ldap.groupMemberAttr = member
 */
public class LdapOrgService implements OrgService {

    private final String url, base, user, password;
    private final String userFilter, groupFilter, groupMemberAttr;
    private final String userBase, groupBase;

    public LdapOrgService(Properties props) {
        this.url = props.getProperty("ldap.url", "ldap://localhost:389");
        this.base = props.getProperty("ldap.base", "dc=example,dc=com");
        this.user = props.getProperty("ldap.user", null);
        this.password = props.getProperty("ldap.password", null);
        this.userFilter = props.getProperty("ldap.userFilter", "(&(objectClass=user)(sAMAccountName={0}))");
        this.groupFilter = props.getProperty("ldap.groupFilter", "(&(objectClass=group)(member={0}))");
        this.groupMemberAttr = props.getProperty("ldap.groupMemberAttr", "member");
        this.userBase = props.getProperty("ldap.userBase", base);
        this.groupBase = props.getProperty("ldap.groupBase", base);
    }

    private DirContext connect() throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, url);
        if (user != null) {
            env.put(Context.SECURITY_AUTHENTICATION, "simple");
            env.put(Context.SECURITY_PRINCIPAL, user);
            env.put(Context.SECURITY_CREDENTIALS, password);
        }
        return new InitialDirContext(env);
    }

    @Override
    public OrgUser getUser(String uid) {
        try {
            DirContext ctx = connect();
            try {
                SearchControls sc = new SearchControls();
                sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
                sc.setReturningAttributes(new String[]{"sAMAccountName", "cn", "mail", "department", "manager"});
                NamingEnumeration<SearchResult> results = ctx.search(userBase,
                    userFilter.replace("{0}", escape(uid)), sc);
                if (results.hasMore()) {
                    SearchResult sr = results.next();
                    Attributes attrs = sr.getAttributes();
                    return new OrgUser(
                        attr(attrs, "sAMAccountName", uid),
                        attr(attrs, "cn", uid),
                        attr(attrs, "mail", null),
                        attr(attrs, "department", null),
                        extractUid(attr(attrs, "manager", null)));
                }
            } finally { ctx.close(); }
        } catch (NamingException ignored) {}
        return null;
    }

    @Override
    public List<String> getGroups(String uid) {
        List<String> groups = new ArrayList<>();
        try {
            DirContext ctx = connect();
            try {
                String userDn = getUserDn(ctx, uid);
                if (userDn == null) return groups;
                SearchControls sc = new SearchControls();
                sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
                sc.setReturningAttributes(new String[]{"cn"});
                NamingEnumeration<SearchResult> results = ctx.search(groupBase,
                    groupFilter.replace("{0}", escape(userDn)), sc);
                while (results.hasMore()) {
                    groups.add(attr(results.next().getAttributes(), "cn", ""));
                }
            } finally { ctx.close(); }
        } catch (NamingException ignored) {}
        return groups;
    }

    @Override
    public List<String> getGroupMembers(String group) {
        List<String> members = new ArrayList<>();
        try {
            DirContext ctx = connect();
            try {
                String groupDn = getGroupDn(ctx, group);
                if (groupDn == null) return members;
                SearchControls sc = new SearchControls();
                sc.setSearchScope(SearchControls.OBJECT_SCOPE);
                sc.setReturningAttributes(new String[]{groupMemberAttr});
                NamingEnumeration<SearchResult> results = ctx.search(groupDn, "(objectClass=*)", sc);
                if (results.hasMore()) {
                    Attribute memberAttr = results.next().getAttributes().get(groupMemberAttr);
                    if (memberAttr != null) {
                        NamingEnumeration<?> vals = memberAttr.getAll();
                        while (vals.hasMore()) {
                            members.add(extractUid((String) vals.next()));
                        }
                    }
                }
            } finally { ctx.close(); }
        } catch (NamingException ignored) {}
        return members;
    }

    @Override
    public String getManager(String uid) {
        OrgUser u = getUser(uid);
        return u != null ? u.getManager() : null;
    }

    // -- helpers --

    private String getUserDn(DirContext ctx, String uid) throws NamingException {
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        NamingEnumeration<SearchResult> r = ctx.search(userBase,
            userFilter.replace("{0}", escape(uid)), sc);
        return r.hasMore() ? r.next().getNameInNamespace() : null;
    }

    private String getGroupDn(DirContext ctx, String group) throws NamingException {
        SearchControls sc = new SearchControls();
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE);
        NamingEnumeration<SearchResult> r = ctx.search(groupBase,
            "(&(objectClass=group)(cn=" + escape(group) + "))", sc);
        return r.hasMore() ? r.next().getNameInNamespace() : null;
    }

    private static String attr(Attributes a, String name, String def) {
        Attribute at = a.get(name);
        if (at == null) return def;
        try { return (String) at.get(); } catch (NamingException e) { return def; }
    }

    /** Extract uid from a DN like "CN=John,OU=Users,DC=company,DC=com" */
    private static String extractUid(String dn) {
        if (dn == null) return null;
        // Try CN= first
        for (String part : dn.split(",")) {
            String trimmed = part.trim();
            if (trimmed.startsWith("CN=") || trimmed.startsWith("cn="))
                return trimmed.substring(3);
            if (trimmed.startsWith("UID=") || trimmed.startsWith("uid="))
                return trimmed.substring(4);
        }
        return dn;
    }

    /** Basic LDAP filter escape */
    private static String escape(String s) {
        return s.replace("\\", "\\5c")
                .replace("*", "\\2a").replace("(", "\\28")
                .replace(")", "\\29").replace("\0", "\\00");
    }
}
