import { apiClient, type RequestSignal } from './http'
import type { AccessRole, CredentialBatchResponse, CredentialDeliveryResult, Employee, EmployeeImportRequest, EmployeeImportResponse, EmployeeRequest } from './types'

export const employeeService = {
  async list(options?: RequestSignal): Promise<Employee[]> { return (await apiClient.get<Employee[]>('/employees', options)).data },
  async create(payload: EmployeeRequest): Promise<Employee> { return (await apiClient.post<Employee>('/employees', payload)).data },
  async import(payload: EmployeeImportRequest): Promise<EmployeeImportResponse> { return (await apiClient.post<EmployeeImportResponse>('/employees/import', payload)).data },
  async sendCredentials(id: string): Promise<CredentialDeliveryResult> { return (await apiClient.post<CredentialDeliveryResult>(`/employees/${encodeURIComponent(id)}/credentials/send`)).data },
  async inviteRoles(id: string): Promise<AccessRole[]> { return (await apiClient.get<AccessRole[]>(`/employees/${encodeURIComponent(id)}/invite-roles`)).data },
  async invite(id: string, roleId: string, roleScope: 'ORGANIZATION' | 'SPACE'): Promise<CredentialDeliveryResult> { return (await apiClient.post<CredentialDeliveryResult>(`/employees/${encodeURIComponent(id)}/invite`, { roleId, roleScope })).data },
  async sendAllCredentials(): Promise<CredentialBatchResponse> { return (await apiClient.post<CredentialBatchResponse>('/employees/credentials/send-all')).data },
  async updateEmail(id: string, email: string): Promise<Employee> { return (await apiClient.patch<Employee>(`/employees/${encodeURIComponent(id)}/email`, { email })).data },
}
