import type { EffectiveAccess } from './types'

export const canOrganization = (access: EffectiveAccess | null, key: string) => !!access && (access.organizationSuperAdmin || access.organizationPermissions.includes(key))
export const canSpace = (access: EffectiveAccess | null, spaceId: string, key: string) => !!access && (access.organizationSuperAdmin || access.organizationPermissions.includes(key) || (access.spacePermissions[spaceId] ?? []).includes(key))
export const canProject = (access: EffectiveAccess | null, projectId: string, spaceId: string, key: string) => !!access && (canSpace(access, spaceId, key) || (access.projectPermissions[projectId] ?? []).includes(key))
