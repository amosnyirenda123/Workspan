import { apiClient } from './http'
import type { AccessManagementContext, AccessMember, AccessRole, AccessRoleRequest, EffectiveAccess, PermissionDefinition, RoleAssignment, RoleScope } from './types'

const query = (scope: RoleScope, contextId: string) => `scope=${scope}&contextId=${encodeURIComponent(contextId)}`

export const accessService = {
  async managementContext(): Promise<AccessManagementContext> { return (await apiClient.get<AccessManagementContext>('/access/management-context')).data },
  async effective(): Promise<EffectiveAccess> { return (await apiClient.get<EffectiveAccess>('/access/effective')).data },
  async permissions(): Promise<PermissionDefinition[]> { return (await apiClient.get<PermissionDefinition[]>('/access/permissions')).data },
  async members(scope: RoleScope, contextId: string): Promise<AccessMember[]> { return (await apiClient.get<AccessMember[]>(`/access/members?${query(scope, contextId)}`)).data },
  async roles(scope: RoleScope, contextId: string): Promise<AccessRole[]> { return (await apiClient.get<AccessRole[]>(`/access/roles?${query(scope, contextId)}`)).data },
  async assignments(scope: RoleScope, contextId: string): Promise<RoleAssignment[]> { return (await apiClient.get<RoleAssignment[]>(`/access/assignments?${query(scope, contextId)}`)).data },
  async createRole(payload: AccessRoleRequest): Promise<AccessRole> { return (await apiClient.post<AccessRole>('/access/roles', payload)).data },
  async updateRole(id: string, payload: AccessRoleRequest): Promise<AccessRole> { return (await apiClient.put<AccessRole>(`/access/roles/${encodeURIComponent(id)}`, payload)).data },
  async removeRole(id: string, scope: RoleScope, contextId: string): Promise<void> { await apiClient.delete(`/access/roles/${encodeURIComponent(id)}?${query(scope, contextId)}`) },
  async assign(scope: RoleScope, contextId: string, userId: string, roleId: string): Promise<RoleAssignment> { return (await apiClient.post<RoleAssignment>(`/access/assignments?${query(scope, contextId)}`, { userId, roleId })).data },
  async unassign(scope: RoleScope, contextId: string, userId: string, roleId: string): Promise<void> { await apiClient.delete(`/access/assignments?${query(scope, contextId)}&userId=${encodeURIComponent(userId)}&roleId=${encodeURIComponent(roleId)}`) },
}
