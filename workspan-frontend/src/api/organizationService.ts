import { apiClient, type RequestSignal } from './http'
import type { Organization } from './types'

export const organizationService = {
  async list(options?: RequestSignal): Promise<Organization[]> { return (await apiClient.get<Organization[]>('/organizations', options)).data },
  async get(id: string, options?: RequestSignal): Promise<Organization> { return (await apiClient.get<Organization>(`/organizations/${encodeURIComponent(id)}`, options)).data },
}
