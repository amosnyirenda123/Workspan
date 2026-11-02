import { useEffect, useState } from 'react'
import { ProjectsManager } from '../projects/ProjectsManager'
import { SpacesManager } from '../spaces/SpacesManager'
import { TasksManager } from '../tasks/TasksManager'
import { ResourcesManager } from '../resources/ResourcesManager'
import { AccessManager } from '../access/AccessManager'
import { accessService } from '../../api/accessService'
import type { AccessManagementContext } from '../../api/types'
import { canOrganization, canSpace } from '../../api/accessPermissions'
import type { EffectiveAccess } from '../../api/types'

type Section = 'spaces' | 'projects' | 'tasks' | 'resources' | 'access'

export type WorkspaceSection = Section

export function WorkspaceCrud({ onChange, section, onSectionChange }: { onChange: () => void; section: WorkspaceSection; onSectionChange: (section: WorkspaceSection) => void }) {
  const setSection = onSectionChange
  const [accessContext, setAccessContext] = useState<AccessManagementContext | null>(null)
  const [effective, setEffective] = useState<EffectiveAccess | null>(null)

  useEffect(() => {
    accessService.managementContext().then(setAccessContext).catch(() => setAccessContext(null))
    accessService.effective().then(setEffective).catch(() => setEffective(null))
  }, [])
  const canManageAccess = !!accessContext && (accessContext.canManageOrganization || accessContext.spaces.length > 0 || accessContext.projects.length > 0)
  const hasSpaceRight = (key: string) => canOrganization(effective, key) || Object.keys(effective?.spacePermissions ?? {}).some(spaceId => canSpace(effective, spaceId, key))
  const hasProjectRight = (key: string) => canOrganization(effective, key) || Object.keys(effective?.spacePermissions ?? {}).some(spaceId => canSpace(effective, spaceId, key)) || Object.values(effective?.projectPermissions ?? {}).some(keys => keys.includes(key))
  const showSpaces = ['space.view', 'space.edit', 'space.delete', 'space.create', 'project.create', 'employee.view', 'employee.create', 'resource.pool.manage'].some(hasSpaceRight)
    || ['project.view', 'project.edit', 'project.delete', 'project.resources.manage', 'task.view', 'task.create', 'task.edit', 'task.delete', 'access.manage'].some(hasProjectRight)
  const showProjects = ['project.view', 'project.create', 'project.edit', 'project.delete', 'project.resources.manage', 'task.view', 'task.create', 'task.edit', 'task.delete', 'access.manage'].some(key => hasProjectRight(key) || hasSpaceRight(key))
  const showTasks = ['task.view', 'task.create', 'task.edit', 'task.delete', 'project.resources.manage'].some(hasProjectRight)
  const showResources = hasSpaceRight('resource.pool.manage') || hasProjectRight('resource.pool.manage')

  useEffect(() => {
    if (section === 'spaces' && !showSpaces) onSectionChange(showProjects ? 'projects' : showTasks ? 'tasks' : showResources ? 'resources' : canManageAccess ? 'access' : 'spaces')
    else if (section === 'projects' && !showProjects) onSectionChange(showTasks ? 'tasks' : showResources ? 'resources' : canManageAccess ? 'access' : showSpaces ? 'spaces' : 'spaces')
    else if (section === 'tasks' && !showTasks) onSectionChange(showResources ? 'resources' : canManageAccess ? 'access' : showProjects ? 'projects' : 'spaces')
    else if (section === 'resources' && !showResources) onSectionChange(canManageAccess ? 'access' : showProjects ? 'projects' : showTasks ? 'tasks' : 'spaces')
    else if (section === 'access' && !canManageAccess) onSectionChange(showResources ? 'resources' : showProjects ? 'projects' : showTasks ? 'tasks' : 'spaces')
  }, [section, showSpaces, showProjects, showTasks, showResources, canManageAccess, onSectionChange])

  return <section className="workspace-crud">
    <div className="crud-heading"><div><p className="eyebrow">GESTION DU TRAVAIL</p><h2>Espaces, projets et tâches</h2><p>Créez les éléments dans cet ordre : espace, projet, puis tâche.</p></div><button className="secondary-button" onClick={onChange}>Actualiser le résumé</button></div>
    <div className="crud-tabs" role="tablist" aria-label="Ressources de l’espace de travail">
      {showSpaces && <button role="tab" aria-selected={section === 'spaces'} className={section === 'spaces' ? 'active' : ''} onClick={() => setSection('spaces')}>Espaces</button>}
      {showProjects && <button role="tab" aria-selected={section === 'projects'} className={section === 'projects' ? 'active' : ''} onClick={() => setSection('projects')}>Projets</button>}
      {showResources && <button role="tab" aria-selected={section === 'resources'} className={section === 'resources' ? 'active' : ''} onClick={() => setSection('resources')}>Pools de ressources</button>}
      {canManageAccess && <button role="tab" aria-selected={section === 'access'} className={section === 'access' ? 'active' : ''} onClick={() => setSection('access')}>Rôles et permissions</button>}
      {showTasks && <button role="tab" aria-selected={section === 'tasks'} className={section === 'tasks' ? 'active' : ''} onClick={() => setSection('tasks')}>Tâches</button>}
    </div>
    {section === 'resources' && showResources && <ResourcesManager access={effective} />}
    {section === 'access' && canManageAccess && <AccessManager />}
    {section === 'spaces' && showSpaces && <SpacesManager access={effective} onChange={onChange} />}
    {section === 'projects' && showProjects && <ProjectsManager access={effective} onChange={onChange} />}
    {section === 'tasks' && showTasks && <TasksManager access={effective} onChange={onChange} />}
    {!showSpaces && !showProjects && !showTasks && !showResources && !canManageAccess && <div className="empty-state">Aucun accès n’a encore été attribué à votre compte.</div>}
  </section>
}
