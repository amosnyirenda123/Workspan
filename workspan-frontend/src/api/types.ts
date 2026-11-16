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

/** baselineStart/baselineEnd = planned dates; startDate/endDate = actual dates (derived from tasks). */
export type Project = {
  id: string; spaceId: string; name: string; description: string | null
  baselineStart: string | null; baselineEnd: string | null
  startDate: string | null; endDate: string | null; createdAt: string
}
export type ProjectRequest = { spaceId: string; name: string; description?: string | null; baselineStart?: string | null; baselineEnd?: string | null }
export type ResourceType = 'HUMAN' | 'EQUIPMENT' | 'BUDGET'
export type Resource = { id: string; spaceId: string; name: string; type: ResourceType; status: string; quantity: number | null; availableQuantity: number | null; amount: number | null; consumed: number | null; remaining: number | null; availableAmount: number | null; currency: string | null; email: string | null; jobTitle: string | null }
export type ResourceRequest = { type: 'EQUIPMENT' | 'BUDGET'; name: string; quantity?: number; amount?: number; currency?: string }
export type ProjectResourceAllocation = { resourceId: string; name: string; type: ResourceType; quantity: number | null; amount: number | null; currency: string | null; allocatedAt: string; activeTaskCount: number; scheduledTaskCount: number; taskAssignedQuantity: number | null; scheduledTaskQuantity: number | null; taskAssignedAmount: number | null; scheduledTaskAmount: number | null; freeQuantity: number | null; freeAmount: number | null; startDate: string | null; endDate: string | null; availableFrom: string | null; active: boolean }
export type TaskResourceAllocation = { resourceId: string; name: string; type: ResourceType; quantity: number | null; amount: number | null; currency: string | null; allocatedAt: string; startDate: string | null; endDate: string | null; availableFrom: string | null; active: boolean; allocationPercent: number | null }
export type TaskResourceOption = { resourceId: string; name: string; type: ResourceType; availableQuantity: number | null; availableAmount: number | null; currency: string | null; assignedToTask: boolean }
export type ProjectResourceAllocationRequest = { resourceId: string; quantity?: number; amount?: number; allocationPercent?: number }
export type RoleScope = 'ORGANIZATION' | 'SPACE' | 'PROJECT'
export type PermissionDefinition = { key: string; label: string; description: string; category: string; action: string; scopes: RoleScope[] }
export type AccessScopeOption = { id: string; name: string; parentName: string | null }
export type AccessManagementContext = { organizationId: string; canManageOrganization: boolean; spaces: AccessScopeOption[]; projects: AccessScopeOption[] }
export type EffectiveAccess = { userId: string; organizationId: string; organizationSuperAdmin: boolean; organizationPermissions: string[]; spacePermissions: Record<string, string[]>; projectPermissions: Record<string, string[]> }
export type AccessMember = { userId: string; displayName: string; email: string }
export type AccessRole = { id: string; name: string; scope: RoleScope; contextId: string; permissionKeys: string[] }
export type AccessRoleRequest = { name: string; scope: RoleScope; contextId: string; permissionKeys: string[] }
export type RoleAssignment = { userId: string; displayName: string; email: string; roleId: string; roleName: string; scope: RoleScope; assignedAt: string }

export type TaskStatus = 'TODO' | 'STARTED' | 'BLOCKED' | 'DONE'
/** baselineStart/baselineEnd = planned dates; startDate/endDate = actual dates. */
export type Task = {
  id: string; projectId: string; name: string; status: TaskStatus
  baselineStart: string | null; baselineEnd: string | null
  startDate: string | null; endDate: string | null; progress: number; createdAt: string
}
export type TaskRequest = { projectId: string; name: string; status: TaskStatus; baselineStart?: string | null; baselineEnd?: string | null; startDate?: string | null; endDate?: string | null; progress?: number | null }

export type DependencyType = 'FINISH_TO_START' | 'START_TO_START' | 'FINISH_TO_FINISH' | 'START_TO_FINISH'
export type TaskDependency = { id: string; predecessorTaskId: string; predecessorName: string; successorTaskId: string; successorName: string; type: DependencyType; lag: number }
export type TaskDependencyRequest = { predecessorTaskId: string; successorTaskId: string; type: DependencyType; lag: number }
export type TaskScheduleRequest = { baselineStart: string | null; baselineEnd: string | null; startDate: string | null; endDate: string | null; progress: number | null }
export type GanttBar = { offsetDays: number; durationDays: number }
export type GanttHealth = 'NO_TASKS' | 'ON_TRACK' | 'AT_RISK' | 'LATE' | 'COMPLETED' | 'COMPLETED_LATE'
export type GanttScheduleState = 'COMPLETED_ON_TIME' | 'COMPLETED_LATE' | 'UNSCHEDULED' | 'BLOCKED' | 'LATE' | 'NOT_STARTED' | 'BEHIND' | 'AHEAD' | 'ON_TRACK'
export type GanttSummary = {
  id: string; name: string; plannedStart: string | null; plannedEnd: string | null; actualStart: string | null; actualEnd: string | null
  forecastEnd: string | null; finishVarianceDays: number | null; projectMarginDays: number | null; plannedDurationDays: number | null
  progress: number; expectedProgress: number; schedulePerformanceIndex: number | null; health: GanttHealth
  taskCount: number; completedTasks: number; lateTasks: number; criticalTasks: number; unscheduledTasks: number; dependencyViolations: number
}
export type GanttTaskRow = {
  id: string; name: string; status: TaskStatus; progress: number
  plannedStart: string | null; plannedEnd: string | null; plannedDurationDays: number | null
  actualStart: string | null; actualEnd: string | null; forecastStart: string | null; forecastEnd: string | null
  earlyStart: string | null; earlyFinish: string | null; lateStart: string | null; lateFinish: string | null
  totalFloatDays: number | null; freeFloatDays: number | null; critical: boolean
  startVarianceDays: number | null; finishVarianceDays: number | null; expectedProgress: number
  scheduleState: GanttScheduleState; late: boolean; scheduled: boolean
  plannedBar: GanttBar | null; forecastBar: GanttBar | null; actualBar: GanttBar | null; warnings: string[]
}
export type GanttLink = { id: string; predecessorTaskId: string; successorTaskId: string; type: DependencyType; lag: number; critical: boolean; violated: boolean; violation: string | null }
export type Gantt = {
  project: GanttSummary
  timeline: { start: string; end: string; today: string; totalDays: number }
  tasks: GanttTaskRow[]; dependencies: GanttLink[]; criticalPath: string[]
}
export type WorkloadAssignment = {
  taskId: string | null; taskName: string; projectId: string | null; projectName: string; visible: boolean
  status: TaskStatus; start: string | null; end: string | null; load: number
  critical: boolean; totalFloatDays: number | null; freeFloatDays: number | null; shiftable: boolean; overloaded: boolean; scheduled: boolean
}
export type WorkloadPeriod = { start: string; end: string; days: number; peakLoad: number; peakUtilisation: number; taskIds: string[] }
export type WorkloadResource = {
  resourceId: string; name: string; type: ResourceType; jobTitle: string | null; capacity: number; unit: 'PERCENT' | 'UNITS'
  load: number[]; utilisation: number[]; peakLoad: number; peakUtilisation: number; averageUtilisation: number; overloadDays: number
  overloads: WorkloadPeriod[]; assignments: WorkloadAssignment[]
}
export type Workload = {
  from: string; to: string; today: string; days: number
  summary: { resourcesAnalysed: number; overloadedResources: number; overloadDays: number; peakUtilisation: number; averageUtilisation: number; unscheduledAssignments: number }
  resources: WorkloadResource[]
}
export type WorkloadCandidate = { resourceId: string; name: string; jobTitle: string | null; inProjectPool: boolean; peakUtilisationWithTask: number; averageUtilisation: number; fits: boolean }
export type LevelingAction = {
  type: 'SHIFT' | 'REASSIGN'; taskId: string; taskName: string; projectName: string
  fromResourceId: string | null; fromResourceName: string | null; shiftDays: number | null; newStart: string | null; newEnd: string | null
  toResourceId: string | null; toResourceName: string | null; requiresPoolAddition: boolean; reason: string
}
export type LevelingProposal = { overloadDaysBefore: number; overloadDaysAfter: number; actions: LevelingAction[]; unresolved: string[] }
export type RescheduleResult = { movedTasks: number; moves: { taskId: string; name: string; previousStart: string | null; previousEnd: string | null; newStart: string; newEnd: string }[] }
