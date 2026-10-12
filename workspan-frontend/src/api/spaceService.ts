import { apiClient, type RequestSignal } from './http'
import type { Space, SpaceRequest } from './types'

export const spaceService = {
  async list(options?: RequestSignal): Promise<Space[]> { return (await apiClient.get<Space[]>('/spaces', options)).data },
  async get(id: string): Promise<Space> { return (await apiClient.get<Space>(`/spaces/${encodeURIComponent(id)}`)).data },
  async create(payload: SpaceRequest): Promise<Space> { return (await apiClient.post<Space>('/spaces', payload)).data },
  async update(id: string, payload: SpaceRequest): Promise<Space> { return (await apiClient.put<Space>(`/spaces/${encodeURIComponent(id)}`, payload)).data },
  async remove(id: string): Promise<void> { await apiClient.delete(`/spaces/${encodeURIComponent(id)}`) },
}
