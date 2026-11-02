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
export type ResourceType = 'HUMAN' | 'EQUIPMENT' | 'BUDGET'
export type Resource = { id: string; spaceId: string; name: string; type: ResourceType; status: string; quantity: number | null; availableQuantity: number | null; amount: number | null; consumed: number | null; remaining: number | null; availableAmount: number | null; currency: string | null; email: string | null; jobTitle: string | null }
export type ResourceRequest = { type: 'EQUIPMENT' | 'BUDGET'; name: string; quantity?: number; amount?: number; currency?: string }
export type ProjectResourceAllocation = { resourceId: string; name: string; type: ResourceType; quantity: number | null; amount: number | null; currency: string | null; allocatedAt: string; activeTaskCount: number; scheduledTaskCount: number; taskAssignedQuantity: number | null; scheduledTaskQuantity: number | null; taskAssignedAmount: number | null; scheduledTaskAmount: number | null; freeQuantity: number | null; freeAmount: number | null; startDate: string | null; endDate: string | null; availableFrom: string | null; active: boolean }
export type TaskResourceAllocation = { resourceId: string; name: string; type: ResourceType; quantity: number | null; amount: number | null; currency: string | null; allocatedAt: string; startDate: string | null; endDate: string | null; availableFrom: string | null; active: boolean }
export type TaskResourceOption = { resourceId: string; name: string; type: ResourceType; availableQuantity: number | null; availableAmount: number | null; currency: string | null; assignedToTask: boolean }
export type ProjectResourceAllocationRequest = { resourceId: string; quantity?: number; amount?: number }
export type RoleScope = 'ORGANIZATION' | 'SPACE' | 'PROJECT'
export type PermissionDefinition = { key: string; label: string; description: string; category: string; action: string }
export type AccessScopeOption = { id: string; name: string; parentName: string | null }
export type AccessManagementContext = { organizationId: string; canManageOrganization: boolean; spaces: AccessScopeOption[]; projects: AccessScopeOption[] }
export type EffectiveAccess = { userId: string; organizationId: string; organizationSuperAdmin: boolean; organizationPermissions: string[]; spacePermissions: Record<string, string[]>; projectPermissions: Record<string, string[]> }
export type AccessMember = { userId: string; displayName: string; email: string }
export type AccessRole = { id: string; name: string; scope: RoleScope; contextId: string; permissionKeys: string[] }
export type AccessRoleRequest = { name: string; scope: RoleScope; contextId: string; permissionKeys: string[] }
export type RoleAssignment = { userId: string; displayName: string; email: string; roleId: string; roleName: string; scope: RoleScope; assignedAt: string }

export type TaskStatus = 'TODO' | 'STARTED' | 'BLOCKED' | 'DONE'
export type Task = {
  id: string; projectId: string; name: string; status: TaskStatus
  startDate: string | null; endDate: string | null; createdAt: string
}
export type TaskRequest = { projectId: string; name: string; status: TaskStatus; startDate?: string | null; endDate?: string | null }
