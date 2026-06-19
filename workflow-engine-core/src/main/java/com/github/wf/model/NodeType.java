package com.github.wf.model;

public enum NodeType {
    START_EVENT,
    END_EVENT,
    USER_TASK,
    SERVICE_TASK,
    EXCLUSIVE_GATEWAY,
    PARALLEL_GATEWAY,
    INCLUSIVE_GATEWAY,
    TIMER
}
