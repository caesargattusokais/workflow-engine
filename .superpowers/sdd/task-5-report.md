# Task 5 Fix Report: EndEventRunner Non-CallActivityNode Guard

**Date:** 2026-07-18
**File:** `workflow-engine-core/src/main/java/com/github/wf/engine/runner/EndEventRunner.java`

## Issue From Review

From `task-5-review-report.md`, Issue #1 (Medium severity):

> Non-CallActivityNode parent wake-up silently fails, leaving parent stuck.

When the parent execution's current node is not a `CallActivityNode` (data integrity violation), the `if (callActivityNode instanceof CallActivityNode ca)` block is skipped silently. The parent execution stays WAITING forever because:
- Variable write-back is skipped
- `parentExec.setStatus(ACTIVE)` is not called
- The parent execution remains WAITING, and the trigger loop skips WAITING executions

The parent trigger `parentTrigger.accept()` still fires, but it's a no-op since the execution is WAITING.

## Changes Applied

### 1. Added Apache Commons Logging

```java
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

private static final Log log = LogFactory.getLog(EndEventRunner.class);
```

This matches the existing logging pattern used by `UserTaskRunner`, `ServiceTaskRunner`, and `TimerRunner`.

### 2. Restructured: parentExec lookup before parentDef loading

**Before:**
```java
parentInst = findById(...)     // check parentInst != null
parentDef = findLatestById(...) // load definition
parentExec = findExecutionById(...) // then check execution
```

**After:**
```java
parentInst = findById(...)     // check parentInst != null
parentExec = findExecutionById(...) // check execution FIRST
parentDef = findLatestById(...) // load definition only if execution exists
```

This avoids a wasted DB call to load the parent process definition when there is no parent execution to operate on, and eliminates the NPE risk described in the review.

### 3. Added `else` branch for non-CallActivityNode

```java
if (callActivityNode instanceof CallActivityNode ca) {
    // ... existing write-back and advancement logic
} else {
    // Parent execution is not at a CallActivityNode — data integrity issue.
    // Unstick the parent by setting it back to ACTIVE.
    String nodeDesc = callActivityNode != null
        ? callActivityNode.getId() + " (" + callActivityNode.getClass().getSimpleName() + ")"
        : "null";
    log.warn("EndEvent: parent execution " + parentExec.getId()
        + " is at node " + nodeDesc
        + ", expected CallActivityNode; unsticking to ACTIVE");
    parentExec.setStatus(ExecutionStatus.ACTIVE);
    repo.updateExecution(parentExec);
}
```

This handles three scenarios:
- **Node is null** (`parentDef.getNode()` returned null): logs "node null", sets execution to ACTIVE. The parent trigger loop will encounter a null node at the execution position, which is better than being stuck in WAITING forever.
- **Node exists but is a different type** (e.g., UserTask, ServiceTask): logs the actual node type, sets execution to ACTIVE. The trigger loop will re-dispatch to the correct runner.
- **Node is CallActivityNode** (normal path): unchanged, full write-back and advancement.

In all else-branch cases, the parent trigger still fires (line 85), and the child execution is marked COMPLETED normally.

## Verification

- Tests could not be run in this environment (no JDK available). The change is a pure Java structural change with no new dependencies (commons-logging is already a direct dependency of `workflow-engine-core`).
- The existing happy-path logic (CallActivityNode instanceof match) is unchanged — all variable write-back, execution advancement, and trigger behavior is preserved.
- The new else-branch uses the same `repo.updateExecution()` call pattern as the existing code.
