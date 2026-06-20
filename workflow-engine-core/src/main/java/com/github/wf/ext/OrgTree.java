package com.github.wf.ext;

import java.util.ArrayList;
import java.util.List;

/** Hierarchical organization node for frontend tree display. */
public class OrgTree {
    private String uid;
    private String name;
    private String title;
    private List<OrgTree> children = new ArrayList<>();

    public OrgTree() {}
    public OrgTree(String uid, String name, String title) {
        this.uid = uid; this.name = name; this.title = title;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<OrgTree> getChildren() { return children; }
    public void setChildren(List<OrgTree> children) { this.children = children; }
}
