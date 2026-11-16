import { apiClient, type RequestSignal } from './http'
import type { LevelingAction, LevelingProposal, Workload, WorkloadCandidate } from './types'

const id = (value: string) => encodeURIComponent(value)
const period = (from?: string, to?: string) => {
  const params = new URLSearchParams()
  if (from) params.set('from', from)
  if (to) params.set('to', to)
  const query = params.toString()
  return query ? `?${query}` : ''
}

export type WorkloadScope = { kind: 'space' | 'project'; id: string }
const base = (scope: WorkloadScope) => `/workload/${scope.kind === 'space' ? 'spaces' : 'projects'}/${id(scope.id)}`

/** Workload API. Loads, overloads, candidates and leveling proposals are all computed by the backend. */
export const workloadService = {
  async analyse(scope: WorkloadScope, from?: string, to?: string, options?: RequestSignal): Promise<Workload> { return (await apiClient.get<Workload>(`${base(scope)}${period(from, to)}`, options)).data },
  async leveling(scope: WorkloadScope, from?: string, to?: string): Promise<LevelingProposal> { return (await apiClient.get<LevelingProposal>(`${base(scope)}/leveling${period(from, to)}`)).data },
  async applyLeveling(actions: LevelingAction[]): Promise<{ applied: number }> {
    return (await apiClient.post<{ applied: number }>('/workload/leveling/apply', { actions: actions.map(action => ({ type: action.type, taskId: action.taskId, fromResourceId: action.fromResourceId, toResourceId: action.toResourceId, shiftDays: action.shiftDays })) })).data
  },
  async candidates(taskId: string, resourceId: string): Promise<WorkloadCandidate[]> { return (await apiClient.get<WorkloadCandidate[]>(`/workload/tasks/${id(taskId)}/resources/${id(resourceId)}/candidates`)).data },
  async reassign(taskId: string, fromResourceId: string, toResourceId: string): Promise<void> { await apiClient.post('/workload/reassign', { taskId, fromResourceId, toResourceId }) },
  async shift(taskId: string, days: number): Promise<void> { await apiClient.post('/workload/shift', { taskId, days }) },
}
