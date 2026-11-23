import { apiClient } from './http'
import type { Sprint, SprintRequest } from './types'

export const sprintService = {
  async list(projectId: string): Promise<Sprint[]> { return (await apiClient.get<Sprint[]>(`/sprints/projects/${encodeURIComponent(projectId)}`)).data },
  async create(projectId: string, request: SprintRequest): Promise<Sprint> { return (await apiClient.post<Sprint>(`/sprints/projects/${encodeURIComponent(projectId)}`, request)).data },
  async update(id: string, request: SprintRequest): Promise<Sprint> { return (await apiClient.put<Sprint>(`/sprints/${encodeURIComponent(id)}`, request)).data },
  async remove(id: string): Promise<void> { await apiClient.delete(`/sprints/${encodeURIComponent(id)}`) },
  async addTask(sprintId: string, taskId: string): Promise<void> { await apiClient.put(`/sprints/${encodeURIComponent(sprintId)}/tasks/${encodeURIComponent(taskId)}`) },
  async removeTask(sprintId: string, taskId: string): Promise<void> { await apiClient.delete(`/sprints/${encodeURIComponent(sprintId)}/tasks/${encodeURIComponent(taskId)}`) },
}
