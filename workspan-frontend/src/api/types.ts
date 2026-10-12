export type AuthUser = { userId: string; displayName: string; email: string; organizationId: string; organizationName: string }

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
