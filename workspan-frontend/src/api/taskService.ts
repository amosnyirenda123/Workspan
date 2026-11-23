import { apiClient, type RequestSignal } from './http'
import type { Task, TaskActivity, TaskComment, TaskRequest } from './types'

export const taskService = {
  async list(options?: RequestSignal): Promise<Task[]> { return (await apiClient.get<Task[]>('/tasks', options)).data },
  async get(id: string): Promise<Task> { return (await apiClient.get<Task>(`/tasks/${encodeURIComponent(id)}`)).data },
  async create(payload: TaskRequest): Promise<Task> { return (await apiClient.post<Task>('/tasks', payload)).data },
  async update(id: string, payload: TaskRequest): Promise<Task> { return (await apiClient.put<Task>(`/tasks/${encodeURIComponent(id)}`, payload)).data },
  async remove(id: string): Promise<void> { await apiClient.delete(`/tasks/${encodeURIComponent(id)}`) },
  async comments(id: string): Promise<TaskComment[]> { return (await apiClient.get<TaskComment[]>(`/tasks/${encodeURIComponent(id)}/comments`)).data },
  async addComment(id: string, body: string): Promise<TaskComment> { return (await apiClient.post<TaskComment>(`/tasks/${encodeURIComponent(id)}/comments`, { body })).data },
  async activity(id: string): Promise<TaskActivity[]> { return (await apiClient.get<TaskActivity[]>(`/tasks/${encodeURIComponent(id)}/activity`)).data },
}
