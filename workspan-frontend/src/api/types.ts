export type AuthUser = { userId: string; displayName: string; email: string; organizationId: string; organizationName: string; mustChangePassword: boolean }

export type Organization = {
  id: string
  name: string
  description: string | null
  website: string | null
  contactEmail: string | null
  phone: string | null
  spaces: number
  projects: number
  tasks: number
  createdAt: string
}

export type Space = { id: string; organizationId: string; name: string; createdAt: string }
export type SpaceRequest = { name: string }
export type Employee = { id: string; spaceId: string; spaceName: string; name: string; email: string | null; jobTitle: string | null; status: string; hasAccount: boolean; credentialEmailSentAt: string | null; createdAt: string }
export type EmployeeRequest = { spaceId: string; name: string; email: string; jobTitle?: string | null }
export type EmployeeImportRow = EmployeeRequest & { rowNumber: number }
export type EmployeeImportRequest = { employees: EmployeeImportRow[] }
export type EmployeeImportRowResult = { rowNumber: number; status: 'CREATED' | 'SKIPPED'; message: string; employee: Employee | null }
export type EmployeeImportResponse = { created: number; skipped: number; rows: EmployeeImportRowResult[] }
export type CredentialDeliveryResult = { employeeId: string; email: string; status: 'SENT' | 'SKIPPED' | 'FAILED'; message: string; sentAt: string | null }
export type CredentialBatchResponse = { sent: number; skipped: number; failed: number; results: CredentialDeliveryResult[] }

export type Project = {
  id: string; spaceId: string; name: string; description: string | null
  startDate: string | null; endDate: string | null; createdAt: string
}
export type ProjectRequest = { spaceId: string; name: string; description?: string | null; startDate?: string | null; endDate?: string | null }

export type TaskStatus = 'TODO' | 'STARTED' | 'BLOCKED' | 'DONE'
export type Task = {
  id: string; projectId: string; name: string; status: TaskStatus
  startDate: string | null; endDate: string | null; createdAt: string
}
export type TaskRequest = { projectId: string; name: string; status: TaskStatus; startDate?: string | null; endDate?: string | null }
