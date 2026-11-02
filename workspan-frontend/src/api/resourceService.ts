import { apiClient } from './http'
import type { Resource, ResourceRequest } from './types'

export const resourceService = {
  async listForSpace(spaceId: string): Promise<Resource[]> { return (await apiClient.get<Resource[]>(`/spaces/${encodeURIComponent(spaceId)}/resources`)).data },
  async create(spaceId: string, payload: ResourceRequest): Promise<Resource> { return (await apiClient.post<Resource>(`/spaces/${encodeURIComponent(spaceId)}/resources`, payload)).data },
  async remove(spaceId: string, resourceId: string): Promise<void> { await apiClient.delete(`/spaces/${encodeURIComponent(spaceId)}/resources/${encodeURIComponent(resourceId)}`) },
}
