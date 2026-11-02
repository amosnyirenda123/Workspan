import { apiClient } from './http'
import type { TaskResourceAllocation, ProjectResourceAllocationRequest, TaskResourceOption } from './types'

export const taskResourceService = {
  async pool(taskId: string): Promise<TaskResourceOption[]> { return (await apiClient.get<TaskResourceOption[]>(`/tasks/${encodeURIComponent(taskId)}/resource-pool`)).data },
  async assigned(taskId: string): Promise<TaskResourceAllocation[]> { return (await apiClient.get<TaskResourceAllocation[]>(`/tasks/${encodeURIComponent(taskId)}/resources`)).data },
  async assign(taskId: string, payload: ProjectResourceAllocationRequest): Promise<TaskResourceAllocation> { return (await apiClient.post<TaskResourceAllocation>(`/tasks/${encodeURIComponent(taskId)}/resources`, payload)).data },
  async remove(taskId: string, resourceId: string): Promise<void> { await apiClient.delete(`/tasks/${encodeURIComponent(taskId)}/resources/${encodeURIComponent(resourceId)}`) },
}
