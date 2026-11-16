package com.workspan.workspanbackend.service;

import com.workspan.workspanbackend.entity.Project;
import com.workspan.workspanbackend.entity.Task;
import com.workspan.workspanbackend.entity.TaskStatus;
import com.workspan.workspanbackend.repository.ProjectRepository;
import com.workspan.workspanbackend.repository.TaskRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * One-way, idempotent migration to the planned/actual date model.
 * Before this feature, start/end held the <i>planned</i> dates and baselines were empty. For every row whose
 * baselines are still empty, the old dates are copied into the baseline (planned) fields, then actual dates
 * that contradict the task status are cleared (a TODO task cannot have started, an unfinished task cannot have ended).
 * Nothing is lost: the original values are kept as the plan.
 */
@Component
@Order(20)
public class ScheduleDataMigration implements ApplicationRunner {
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final ProjectScheduleSync projectScheduleSync;

    public ScheduleDataMigration(TaskRepository tasks, ProjectRepository projects, ProjectScheduleSync projectScheduleSync) {
        this.tasks = tasks; this.projects = projects; this.projectScheduleSync = projectScheduleSync;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now();
        for (Task task : tasks.findAll()) {
            boolean changed = false;
            if (task.getBaselineStart() == null && task.getBaselineEnd() == null && (task.getStartDate() != null || task.getEndDate() != null)) {
                task.setBaselineStart(task.getStartDate());
                task.setBaselineEnd(task.getEndDate());
                changed = true;
                if (task.getStatus() == TaskStatus.TODO) { task.setStartDate(null); task.setEndDate(null); }
                else if (task.getStatus() != TaskStatus.DONE) {
                    task.setEndDate(null);
                    if (task.getStartDate() != null && task.getStartDate().isAfter(today)) task.setStartDate(null);
                }
            }
            if (task.getStatus() == TaskStatus.DONE && task.getProgress() != 100) { task.setProgress(100); changed = true; }
            if (changed) tasks.save(task);
        }
        for (Project project : projects.findAll()) {
            if (project.getBaselineStart() == null && project.getBaselineEnd() == null && (project.getStartDate() != null || project.getEndDate() != null)) {
                project.setBaselineStart(project.getStartDate());
                project.setBaselineEnd(project.getEndDate());
                projects.save(project);
            }
            projectScheduleSync.refreshActualDates(project);
        }
    }
}
