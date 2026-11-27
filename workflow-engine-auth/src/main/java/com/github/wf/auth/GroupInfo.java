package com.github.wf.auth;

/**
 * Lightweight metadata for a data-sharing group.
 *
 * <p>Membership is NOT stored here: the authoritative source is the
 * {@code groups} / {@code admin_groups} columns of {@code auth_user}. This
 * record only carries the human-facing name and audit fields.
 */
public record GroupInfo(
        String id,
        String name,
        String description,
        String createdBy,
        long createdAt) {

    public GroupInfo {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("group id is blank");
        if (name == null || name.isBlank()) name = id;
        if (description == null) description = "";
        if (createdBy == null) createdBy = "";
    }
}
