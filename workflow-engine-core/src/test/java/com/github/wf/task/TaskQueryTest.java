package com.github.wf.task;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class TaskQueryTest {

    @Test
    void matchesByAssignee() {
        Task task = new Task("t1", "inst-1", "node-1");
        task.setAssignee("张三");
        assertThat(new TaskQuery().assignee("张三").matches(task)).isTrue();
        assertThat(new TaskQuery().assignee("李四").matches(task)).isFalse();
    }

    @Test
    void matchesByCandidateGroup() {
        Task task = new Task("t1", "inst-1", "node-1");
        task.setCandidateGroups(List.of("manager", "hr"));
        assertThat(new TaskQuery().candidateGroup("manager").matches(task)).isTrue();
        assertThat(new TaskQuery().candidateGroup("finance").matches(task)).isFalse();
    }

    @Test
    void matchesByStatus() {
        Task task = new Task("t1", "inst-1", "node-1");
        assertThat(new TaskQuery().status(TaskStatus.PENDING).matches(task)).isTrue();
        assertThat(new TaskQuery().status(TaskStatus.COMPLETED).matches(task)).isFalse();
    }

    @Test
    void chainedBuilder() {
        TaskQuery q = new TaskQuery()
                .assignee("张三")
                .candidateGroup("manager")
                .instanceId("inst-1")
                .status(TaskStatus.PENDING);
        assertThat(q.getAssignee()).isEqualTo("张三");
        assertThat(q.getCandidateGroups()).contains("manager");
        assertThat(q.getInstanceId()).isEqualTo("inst-1");
        assertThat(q.getStatus()).isEqualTo(TaskStatus.PENDING);
    }
}
