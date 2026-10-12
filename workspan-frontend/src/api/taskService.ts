import { apiClient, type RequestSignal } from './http'
import type { Task, TaskRequest } from './types'

export const taskService = {
  async list(options?: RequestSignal): Promise<Task[]> { return (await apiClient.get<Task[]>('/tasks', options)).data },
  async get(id: string): Promise<Task> { return (await apiClient.get<Task>(`/tasks/${encodeURIComponent(id)}`)).data },
  async create(payload: TaskRequest): Promise<Task> { return (await apiClient.post<Task>('/tasks', payload)).data },
  async update(id: string, payload: TaskRequest): Promise<Task> { return (await apiClient.put<Task>(`/tasks/${encodeURIComponent(id)}`, payload)).data },
  async remove(id: string): Promise<void> { await apiClient.delete(`/tasks/${encodeURIComponent(id)}`) },
}
