package com.github.wf.auth;

import java.util.List;
import java.util.Optional;

/** Storage for group metadata. Membership lives in {@link UserStore}. */
public interface GroupStore {

    List<GroupInfo> listAll();

    Optional<GroupInfo> find(String id);

    /** Insert a group. Implementations must reject duplicates. */
    void create(GroupInfo info);

    void delete(String id);
}
