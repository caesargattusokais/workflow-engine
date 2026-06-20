package com.github.wf.ext;

/**
 * Organization user record.
 */
public class OrgUser {
    private final String uid;        // login name / sAMAccountName
    private final String cn;         // display name
    private final String email;
    private final String department;
    private final String manager;    // manager's uid (or DN)

    public OrgUser(String uid, String cn, String email, String department, String manager) {
        this.uid = uid;
        this.cn = cn;
        this.email = email;
        this.department = department;
        this.manager = manager;
    }

    public String getUid() { return uid; }
    public String getCn() { return cn; }
    public String getEmail() { return email; }
    public String getDepartment() { return department; }
    public String getManager() { return manager; }

    @Override
    public String toString() {
        return "OrgUser{uid='" + uid + "', cn='" + cn + "'}";
    }
}
