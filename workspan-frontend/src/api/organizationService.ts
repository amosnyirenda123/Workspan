import { apiClient, type RequestSignal } from './http'
import type { Organization, OrganizationUpdateRequest } from './types'

export const organizationService = {
  async list(options?: RequestSignal): Promise<Organization[]> { return (await apiClient.get<Organization[]>('/organizations', options)).data },
  async get(id: string, options?: RequestSignal): Promise<Organization> { return (await apiClient.get<Organization>(`/organizations/${encodeURIComponent(id)}`, options)).data },
  async update(id: string, payload: OrganizationUpdateRequest): Promise<Organization> { return (await apiClient.put<Organization>(`/organizations/${encodeURIComponent(id)}`, payload)).data },
}
