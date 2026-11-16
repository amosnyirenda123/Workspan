import { apiClient, type RequestSignal } from './http'
import type { Gantt, RescheduleResult, Task, TaskDependency, TaskDependencyRequest, TaskScheduleRequest } from './types'

const id = (value: string) => encodeURIComponent(value)

/** Gantt API. Every calculation (margins, critical path, variances, forecast) is done by the backend. */
export const ganttService = {
  async project(projectId: string, options?: RequestSignal): Promise<Gantt> { return (await apiClient.get<Gantt>(`/gantt/projects/${id(projectId)}`, options)).data },
  async reschedule(projectId: string): Promise<RescheduleResult> { return (await apiClient.post<RescheduleResult>(`/gantt/projects/${id(projectId)}/reschedule`)).data },
  async dependencies(projectId: string): Promise<TaskDependency[]> { return (await apiClient.get<TaskDependency[]>(`/gantt/projects/${id(projectId)}/dependencies`)).data },
  async createDependency(projectId: string, payload: TaskDependencyRequest): Promise<TaskDependency> { return (await apiClient.post<TaskDependency>(`/gantt/projects/${id(projectId)}/dependencies`, payload)).data },
  async updateDependency(dependencyId: string, payload: TaskDependencyRequest): Promise<TaskDependency> { return (await apiClient.put<TaskDependency>(`/gantt/dependencies/${id(dependencyId)}`, payload)).data },
  async removeDependency(dependencyId: string): Promise<void> { await apiClient.delete(`/gantt/dependencies/${id(dependencyId)}`) },
  async updateSchedule(taskId: string, payload: TaskScheduleRequest): Promise<Task> { return (await apiClient.put<Task>(`/gantt/tasks/${id(taskId)}/schedule`, payload)).data },
}
