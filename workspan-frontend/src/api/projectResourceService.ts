import { apiClient } from './http'
import type { ProjectResourceAllocation, ProjectResourceAllocationRequest, Resource } from './types'

export const projectResourceService = {
  async pool(projectId: string): Promise<Resource[]> { return (await apiClient.get<Resource[]>(`/projects/${encodeURIComponent(projectId)}/resource-pool`)).data },
  async assigned(projectId: string): Promise<ProjectResourceAllocation[]> { return (await apiClient.get<ProjectResourceAllocation[]>(`/projects/${encodeURIComponent(projectId)}/resources`)).data },
  async assign(projectId: string, payload: ProjectResourceAllocationRequest): Promise<ProjectResourceAllocation> { return (await apiClient.post<ProjectResourceAllocation>(`/projects/${encodeURIComponent(projectId)}/resources`, payload)).data },
  async remove(projectId: string, resourceId: string): Promise<void> { await apiClient.delete(`/projects/${encodeURIComponent(projectId)}/resources/${encodeURIComponent(resourceId)}`) },
}
