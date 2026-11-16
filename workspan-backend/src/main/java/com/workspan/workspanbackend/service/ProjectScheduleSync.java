package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.Task;
import com.workspan.workspanbackend.entity.TaskStatus;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Keeps a project's <b>actual</b> dates derived from its tasks:
 * actual start = first actual task start; actual end = last actual task end once every task is done.
 */
@Component
public class ProjectScheduleSync {
    private final TaskRepository tasks;
    private final ProjectRepository projects;

    public ProjectScheduleSync(TaskRepository tasks, ProjectRepository projects) {
        this.tasks = tasks; this.projects = projects;
    }

    @Transactional
    public void refreshActualDates(Project project) {
        List<Task> projectTasks = tasks.findAllByProjectIdOrderByNameAsc(project.getId());
        LocalDate actualStart = projectTasks.stream().map(Task::getStartDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        boolean allDone = !projectTasks.isEmpty() && projectTasks.stream().allMatch(task -> task.getStatus() == TaskStatus.DONE);
        LocalDate actualEnd = allDone ? projectTasks.stream().map(Task::getEndDate).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null) : null;
        if (!Objects.equals(project.getStartDate(), actualStart) || !Objects.equals(project.getEndDate(), actualEnd)) {
            project.setStartDate(actualStart);
            project.setEndDate(actualEnd);
            projects.save(project);
        }
    }
}
