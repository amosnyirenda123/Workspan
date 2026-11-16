import { ProjectsManager } from '../projects/ProjectsManager'
import { SpacesManager } from '../spaces/SpacesManager'
import { TasksManager } from '../tasks/TasksManager'
import { ResourcesManager } from '../resources/ResourcesManager'
import { AccessManager } from '../access/AccessManager'
import { AnalyticsManager } from '../analytics/AnalyticsManager'
import { GanttManager } from '../gantt/GanttManager'
import { WorkloadManager } from '../workload/WorkloadManager'
import { canOrganization, canSpace } from '../../api/accessPermissions'
import type { EffectiveAccess } from '../../api/types'

type Section = 'overview' | 'spaces' | 'projects' | 'tasks' | 'gantt' | 'workload' | 'resources' | 'access' | 'analytics'

export type WorkspaceSection = Section

export function WorkspaceCrud({ onChange, section, access, canViewAnalyticsEmployees, canViewAnalyticsPools }: { onChange: () => void; section: WorkspaceSection; access: EffectiveAccess | null; canViewAnalyticsEmployees: boolean; canViewAnalyticsPools: boolean }) {
  const canManageAccess = canOrganization(access, 'access.manage') || Object.values(access?.spacePermissions ?? {}).some(keys => keys.includes('access.manage')) || Object.values(access?.projectPermissions ?? {}).some(keys => keys.includes('access.manage'))
  const hasSpaceRight = (key: string) => canOrganization(access, key) || Object.keys(access?.spacePermissions ?? {}).some(spaceId => canSpace(access, spaceId, key))
  const hasProjectRight = (key: string) => canOrganization(access, key) || Object.keys(access?.spacePermissions ?? {}).some(spaceId => canSpace(access, spaceId, key)) || Object.values(access?.projectPermissions ?? {}).some(keys => keys.includes(key))
  const showSpaces = ['space.view', 'space.edit', 'space.delete', 'space.create', 'project.create'].some(hasSpaceRight)
  const showProjects = ['project.view', 'project.edit', 'project.delete', 'project.resources.manage'].some(hasProjectRight) || hasSpaceRight('project.create')
  const showTasks = ['task.view', 'task.view.kanban', 'task.view.scrum', 'task.create', 'task.edit', 'task.delete', 'project.resources.manage'].some(hasProjectRight)
  const showResources = hasSpaceRight('resource.pool.view') || hasSpaceRight('resource.pool.manage')
  const showAnalytics = hasSpaceRight('analytics.view') || hasProjectRight('analytics.view')
  const showGantt = hasProjectRight('gantt.view')
  const showWorkload = hasSpaceRight('workload.view') || hasProjectRight('workload.view')

  return <section className="workspace-crud">
    {(section === 'spaces' || section === 'projects') && <div className="crud-heading"><div><p className="eyebrow">CONFIGURATION</p><h2>{section === 'spaces' ? 'Espaces' : 'Projets'}</h2><p>{section === 'spaces' ? 'Organisez les &#xE9;quipes et leur p&#xE9;rim&#xE8;tre de travail.' : 'Cr&#xE9;ez les projets dans les espaces auxquels vous avez acc&#xE8;s.'}</p></div><button className="secondary-button" onClick={onChange}>Actualiser</button></div>}
    {section === 'resources' && showResources && <ResourcesManager access={access} />}
    {section === 'access' && canManageAccess && <AccessManager />}
    {section === 'analytics' && showAnalytics && <AnalyticsManager canViewEmployees={canViewAnalyticsEmployees} canViewResourcePools={canViewAnalyticsPools} />}
    {section === 'spaces' && showSpaces && <SpacesManager access={access} onChange={onChange} />}
    {section === 'projects' && showProjects && <ProjectsManager access={access} onChange={onChange} />}
    {section === 'tasks' && showTasks && <TasksManager access={access} onChange={onChange} />}
    {section === 'gantt' && showGantt && <GanttManager access={access} />}
    {section === 'workload' && showWorkload && <WorkloadManager access={access} />}
    {!showSpaces && !showProjects && !showTasks && !showGantt && !showWorkload && !showResources && !canManageAccess && !showAnalytics && <div className="empty-state">Aucun accès n’a encore été attribué à votre compte.</div>}
  </section>
}
