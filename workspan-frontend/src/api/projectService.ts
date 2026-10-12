import { apiClient, type RequestSignal } from './http'
import type { Project, ProjectRequest } from './types'

export const projectService = {
  async list(options?: RequestSignal): Promise<Project[]> { return (await apiClient.get<Project[]>('/projects', options)).data },
  async get(id: string): Promise<Project> { return (await apiClient.get<Project>(`/projects/${encodeURIComponent(id)}`)).data },
  async create(payload: ProjectRequest): Promise<Project> { return (await apiClient.post<Project>('/projects', payload)).data },
  async update(id: string, payload: ProjectRequest): Promise<Project> { return (await apiClient.put<Project>(`/projects/${encodeURIComponent(id)}`, payload)).data },
  async remove(id: string): Promise<void> { await apiClient.delete(`/projects/${encodeURIComponent(id)}`) },
}
