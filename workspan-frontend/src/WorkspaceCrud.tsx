import { useEffect, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from './api/http'
import { projectService } from './api/projectService'
import { spaceService } from './api/spaceService'
import { taskService } from './api/taskService'
import type { Project, ProjectRequest, Space, SpaceRequest, Task, TaskRequest, TaskStatus } from './api/types'

type Section = 'spaces' | 'projects' | 'tasks'
type SpaceForm = { name: string }
type ProjectForm = { spaceId: string; name: string; description: string; startDate: string; endDate: string }
type TaskForm = { projectId: string; name: string; status: TaskStatus; startDate: string; endDate: string }

const emptySpace: SpaceForm = { name: '' }
const emptyProject: ProjectForm = { spaceId: '', name: '', description: '', startDate: '', endDate: '' }
const emptyTask: TaskForm = { projectId: '', name: '', status: 'TODO', startDate: '', endDate: '' }

export function WorkspaceCrud({ onChange }: { onChange: () => void }) {
  const [section, setSection] = useState<Section>('spaces')
  const [spaces, setSpaces] = useState<Space[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [spaceForm, setSpaceForm] = useState<SpaceForm>(emptySpace)
  const [projectForm, setProjectForm] = useState<ProjectForm>(emptyProject)
  const [taskForm, setTaskForm] = useState<TaskForm>(emptyTask)
  const [editingSpaceId, setEditingSpaceId] = useState<string | null>(null)
  const [editingProjectId, setEditingProjectId] = useState<string | null>(null)
  const [editingTaskId, setEditingTaskId] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([
      spaceService.list({ signal: controller.signal }),
      projectService.list({ signal: controller.signal }),
      taskService.list({ signal: controller.signal }),
    ]).then(([spaceData, projectData, taskData]) => {
      setSpaces(spaceData)
      setProjects(projectData)
      setTasks(taskData)
    }).catch(cause => {
      if (!controller.signal.aborted) setError(getApiErrorMessage(cause, 'Impossible de charger les espaces, projets et tâches.'))
    }).finally(() => {
      if (!controller.signal.aborted) setLoading(false)
    })
    return () => controller.abort()
  }, [])

  function clearError() { setError('') }

  async function submitSpace(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true); clearError()
    try {
      const payload: SpaceRequest = { name: spaceForm.name.trim() }
      const saved = editingSpaceId ? await spaceService.update(editingSpaceId, payload) : await spaceService.create(payload)
      setSpaces(current => editingSpaceId ? current.map(item => item.id === saved.id ? saved : item) : [...current, saved].sort((a, b) => a.name.localeCompare(b.name)))
      setSpaceForm(emptySpace); setEditingSpaceId(null); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’enregistrer cet espace.')) }
    finally { setSaving(false) }
  }

  async function submitProject(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true); clearError()
    try {
      const payload: ProjectRequest = {
        spaceId: projectForm.spaceId, name: projectForm.name.trim(),
        description: projectForm.description.trim() || null,
        startDate: projectForm.startDate || null, endDate: projectForm.endDate || null,
      }
      const saved = editingProjectId ? await projectService.update(editingProjectId, payload) : await projectService.create(payload)
      setProjects(current => editingProjectId ? current.map(item => item.id === saved.id ? saved : item) : [...current, saved].sort((a, b) => a.name.localeCompare(b.name)))
      setProjectForm(emptyProject); setEditingProjectId(null); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’enregistrer ce projet.')) }
    finally { setSaving(false) }
  }

  async function submitTask(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true); clearError()
    try {
      const payload: TaskRequest = {
        projectId: taskForm.projectId, name: taskForm.name.trim(), status: taskForm.status,
        startDate: taskForm.startDate || null, endDate: taskForm.endDate || null,
      }
      const saved = editingTaskId ? await taskService.update(editingTaskId, payload) : await taskService.create(payload)
      setTasks(current => editingTaskId ? current.map(item => item.id === saved.id ? saved : item) : [...current, saved].sort((a, b) => a.name.localeCompare(b.name)))
      setTaskForm(emptyTask); setEditingTaskId(null); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’enregistrer cette tâche.')) }
    finally { setSaving(false) }
  }

  async function removeSpace(space: Space) {
    if (!window.confirm(`Supprimer l’espace « ${space.name} » ?`)) return
    setSaving(true); clearError()
    try {
      await spaceService.remove(space.id)
      setSpaces(current => current.filter(item => item.id !== space.id)); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de supprimer cet espace. Il contient peut-être des projets.')) }
    finally { setSaving(false) }
  }

  async function removeProject(project: Project) {
    if (!window.confirm(`Supprimer le projet « ${project.name} » ?`)) return
    setSaving(true); clearError()
    try {
      await projectService.remove(project.id)
      setProjects(current => current.filter(item => item.id !== project.id)); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de supprimer ce projet. Il contient peut-être des tâches.')) }
    finally { setSaving(false) }
  }

  async function removeTask(task: Task) {
    if (!window.confirm(`Supprimer la tâche « ${task.name} » ?`)) return
    setSaving(true); clearError()
    try {
      await taskService.remove(task.id)
      setTasks(current => current.filter(item => item.id !== task.id)); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de supprimer cette tâche.')) }
    finally { setSaving(false) }
  }

  function editSpace(space: Space) { setSection('spaces'); setSpaceForm({ name: space.name }); setEditingSpaceId(space.id); clearError() }
  function editProject(project: Project) { setSection('projects'); setProjectForm({ spaceId: project.spaceId, name: project.name, description: project.description ?? '', startDate: project.startDate ?? '', endDate: project.endDate ?? '' }); setEditingProjectId(project.id); clearError() }
  function editTask(task: Task) { setSection('tasks'); setTaskForm({ projectId: task.projectId, name: task.name, status: task.status, startDate: task.startDate ?? '', endDate: task.endDate ?? '' }); setEditingTaskId(task.id); clearError() }

  const spaceName = (id: string) => spaces.find(space => space.id === id)?.name ?? 'Espace indisponible'
  const projectName = (id: string) => projects.find(project => project.id === id)?.name ?? 'Projet indisponible'

  return <section className="workspace-crud">
    <div className="crud-heading"><div><p className="eyebrow">GESTION DU TRAVAIL</p><h2>Espaces, projets et tâches</h2><p>Créez les éléments dans cet ordre : espace, projet, puis tâche.</p></div><button className="secondary-button" onClick={onChange}>Actualiser le résumé</button></div>
    <div className="crud-tabs" role="tablist" aria-label="Ressources de l’espace de travail">
      <button role="tab" aria-selected={section === 'spaces'} className={section === 'spaces' ? 'active' : ''} onClick={() => setSection('spaces')}>Espaces <span>{spaces.length}</span></button>
      <button role="tab" aria-selected={section === 'projects'} className={section === 'projects' ? 'active' : ''} onClick={() => setSection('projects')}>Projets <span>{projects.length}</span></button>
      <button role="tab" aria-selected={section === 'tasks'} className={section === 'tasks' ? 'active' : ''} onClick={() => setSection('tasks')}>Tâches <span>{tasks.length}</span></button>
    </div>
    {error && <div className="alert crud-alert" role="alert">{error}</div>}
    {loading ? <div className="empty-state">Chargement des ressources…</div> : <div className="crud-layout">
      <section className="form-panel crud-form-panel">
        <div className="form-panel-heading"><div className="form-icon">＋</div><div><h2>{section === 'spaces' ? editingSpaceId ? 'Modifier l’espace' : 'Nouvel espace' : section === 'projects' ? editingProjectId ? 'Modifier le projet' : 'Nouveau projet' : editingTaskId ? 'Modifier la tâche' : 'Nouvelle tâche'}</h2><p>{section === 'spaces' ? 'Les espaces regroupent les projets de votre organisation.' : section === 'projects' ? 'Chaque projet appartient à un espace.' : 'Chaque tâche appartient à un projet.'}</p></div></div>
        {section === 'spaces' && <form className="organization-form" onSubmit={submitSpace}><div className="form-field"><label htmlFor="space-name">Nom de l’espace <span>*</span></label><input id="space-name" required maxLength={50} value={spaceForm.name} onChange={event => setSpaceForm({ name: event.target.value })} /></div><div className="form-actions"><button className="primary-button" disabled={saving}>{saving ? 'Enregistrement…' : editingSpaceId ? 'Enregistrer' : 'Créer l’espace'}</button>{editingSpaceId && <button type="button" className="secondary-button" onClick={() => { setEditingSpaceId(null); setSpaceForm(emptySpace) }}>Annuler</button>}</div></form>}
        {section === 'projects' && <form className="organization-form" onSubmit={submitProject}><div className="form-grid"><div className="form-field full-width"><label htmlFor="project-space">Espace <span>*</span></label><select id="project-space" required value={projectForm.spaceId} onChange={event => setProjectForm({ ...projectForm, spaceId: event.target.value })}><option value="">Choisir un espace</option>{spaces.map(space => <option key={space.id} value={space.id}>{space.name}</option>)}</select></div><div className="form-field full-width"><label htmlFor="project-name">Nom du projet <span>*</span></label><input id="project-name" required maxLength={120} value={projectForm.name} onChange={event => setProjectForm({ ...projectForm, name: event.target.value })} /></div><div className="form-field full-width"><label htmlFor="project-description">Description</label><textarea id="project-description" maxLength={10000} rows={3} value={projectForm.description} onChange={event => setProjectForm({ ...projectForm, description: event.target.value })} /></div><div className="form-field"><label htmlFor="project-start">Date de début</label><input id="project-start" type="date" value={projectForm.startDate} onChange={event => setProjectForm({ ...projectForm, startDate: event.target.value })} /></div><div className="form-field"><label htmlFor="project-end">Date de fin</label><input id="project-end" type="date" value={projectForm.endDate} onChange={event => setProjectForm({ ...projectForm, endDate: event.target.value })} /></div></div>{spaces.length === 0 && <p className="field-hint">Créez un espace avant d’ajouter un projet.</p>}<div className="form-actions"><button className="primary-button" disabled={saving || spaces.length === 0}>{saving ? 'Enregistrement…' : editingProjectId ? 'Enregistrer' : 'Créer le projet'}</button>{editingProjectId && <button type="button" className="secondary-button" onClick={() => { setEditingProjectId(null); setProjectForm(emptyProject) }}>Annuler</button>}</div></form>}
        {section === 'tasks' && <form className="organization-form" onSubmit={submitTask}><div className="form-grid"><div className="form-field full-width"><label htmlFor="task-project">Projet <span>*</span></label><select id="task-project" required value={taskForm.projectId} onChange={event => setTaskForm({ ...taskForm, projectId: event.target.value })}><option value="">Choisir un projet</option>{projects.map(project => <option key={project.id} value={project.id}>{spaceName(project.spaceId)} / {project.name}</option>)}</select></div><div className="form-field full-width"><label htmlFor="task-name">Nom de la tâche <span>*</span></label><input id="task-name" required maxLength={160} value={taskForm.name} onChange={event => setTaskForm({ ...taskForm, name: event.target.value })} /></div><div className="form-field full-width"><label htmlFor="task-status">Statut</label><select id="task-status" value={taskForm.status} onChange={event => setTaskForm({ ...taskForm, status: event.target.value as TaskStatus })}><option value="TODO">À faire</option><option value="STARTED">En cours</option><option value="BLOCKED">Bloquée</option><option value="DONE">Terminée</option></select></div><div className="form-field"><label htmlFor="task-start">Date de début</label><input id="task-start" type="date" value={taskForm.startDate} onChange={event => setTaskForm({ ...taskForm, startDate: event.target.value })} /></div><div className="form-field"><label htmlFor="task-end">Date de fin</label><input id="task-end" type="date" value={taskForm.endDate} onChange={event => setTaskForm({ ...taskForm, endDate: event.target.value })} /></div></div>{projects.length === 0 && <p className="field-hint">Créez un projet avant d’ajouter une tâche.</p>}<div className="form-actions"><button className="primary-button" disabled={saving || projects.length === 0}>{saving ? 'Enregistrement…' : editingTaskId ? 'Enregistrer' : 'Créer la tâche'}</button>{editingTaskId && <button type="button" className="secondary-button" onClick={() => { setEditingTaskId(null); setTaskForm(emptyTask) }}>Annuler</button>}</div></form>}
      </section>

      <section className="crud-list-panel" aria-live="polite">
        {section === 'spaces' && (spaces.length ? spaces.map(space => <article className="crud-row" key={space.id}><div className="crud-symbol violet">E</div><div className="crud-row-main"><strong>{space.name}</strong><small>{projects.filter(project => project.spaceId === space.id).length} projet(s)</small></div><div className="crud-actions"><button className="secondary-button" disabled={saving} onClick={() => editSpace(space)}>Modifier</button><button className="danger-button" disabled={saving} onClick={() => void removeSpace(space)}>Supprimer</button></div></article>) : <div className="empty-state"><strong>Aucun espace</strong><p>Créez votre premier espace pour commencer.</p></div>)}
        {section === 'projects' && (projects.length ? projects.map(project => <article className="crud-row" key={project.id}><div className="crud-symbol blue">P</div><div className="crud-row-main"><strong>{project.name}</strong><small>{spaceName(project.spaceId)}{project.startDate ? ` · ${project.startDate}` : ''}</small></div><div className="crud-actions"><button className="secondary-button" disabled={saving} onClick={() => editProject(project)}>Modifier</button><button className="danger-button" disabled={saving} onClick={() => void removeProject(project)}>Supprimer</button></div></article>) : <div className="empty-state"><strong>Aucun projet</strong><p>Choisissez un espace pour créer votre premier projet.</p></div>)}
        {section === 'tasks' && (tasks.length ? tasks.map(task => <article className="crud-row" key={task.id}><div className="crud-symbol green">T</div><div className="crud-row-main"><strong>{task.name}</strong><small>{projectName(task.projectId)} · {task.status}</small></div><div className="crud-actions"><button className="secondary-button" disabled={saving} onClick={() => editTask(task)}>Modifier</button><button className="danger-button" disabled={saving} onClick={() => void removeTask(task)}>Supprimer</button></div></article>) : <div className="empty-state"><strong>Aucune tâche</strong><p>Choisissez un projet pour créer votre première tâche.</p></div>)}
      </section>
    </div>}
  </section>
}
