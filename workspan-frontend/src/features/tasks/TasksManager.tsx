import { useEffect, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '../../api/http'
import { projectService } from '../../api/projectService'
import { taskService } from '../../api/taskService'
import { TaskResourcesPanel } from './TaskResourcesPanel'
import type { Project, Task, TaskRequest, TaskStatus } from '../../api/types'
import type { EffectiveAccess } from '../../api/types'
import { canProject } from '../../api/accessPermissions'

const emptyForm: TaskRequest = { projectId: '', name: '', status: 'TODO', startDate: '', endDate: '' }
const statusLabels: Record<TaskStatus, string> = { TODO: 'À faire', STARTED: 'En cours', BLOCKED: 'Bloquée', DONE: 'Terminée' }

export function TasksManager({ onChange, access }: { onChange: () => void; access: EffectiveAccess | null }) {
  const [projects, setProjects] = useState<Project[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [form, setForm] = useState<TaskRequest>(emptyForm)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [resourcesTaskId, setResourcesTaskId] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([projectService.list({ signal: controller.signal }), taskService.list({ signal: controller.signal })])
      .then(([projectData, taskData]) => { setProjects(projectData); setTasks(taskData) })
      .catch(cause => { if (!controller.signal.aborted) setError(getApiErrorMessage(cause, 'Impossible de charger les projets et tâches.')) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setError('')
    try {
      const saved = editingId ? await taskService.update(editingId, form) : await taskService.create(form)
      setTasks(current => editingId ? current.map(item => item.id === saved.id ? saved : item) : [...current, saved].sort((a, b) => a.name.localeCompare(b.name)))
      setForm(emptyForm); setEditingId(null); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’enregistrer cette tâche.')) }
    finally { setSaving(false) }
  }

  function edit(task: Task) {
    setForm({ projectId: task.projectId, name: task.name, status: task.status, startDate: task.startDate ?? '', endDate: task.endDate ?? '' })
    setEditingId(task.id); setError('')
  }

  async function remove(task: Task) {
    if (!window.confirm(`Supprimer la tâche « ${task.name} » ?`)) return
    setSaving(true); setError('')
    try {
      await taskService.remove(task.id)
      setTasks(current => current.filter(item => item.id !== task.id)); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de supprimer cette tâche.')) }
    finally { setSaving(false) }
  }

  const projectName = (id: string) => projects.find(project => project.id === id)?.name ?? 'Projet indisponible'
  const canCreate = (projectId: string) => { const project = projects.find(item => item.id === projectId); return !!project && canProject(access, project.id, project.spaceId, 'task.create') }
  const canEdit = (task: Task) => { const project = projects.find(item => item.id === task.projectId); return !!project && canProject(access, project.id, project.spaceId, 'task.edit') }
  const canDelete = (task: Task) => { const project = projects.find(item => item.id === task.projectId); return !!project && canProject(access, project.id, project.spaceId, 'task.delete') }
  const canManageResources = (task: Task) => { const project = projects.find(item => item.id === task.projectId); return !!project && canProject(access, project.id, project.spaceId, 'project.resources.manage') }
  const showForm = projects.some(project => canProject(access, project.id, project.spaceId, 'task.create')) || (!!editingId && tasks.some(task => task.id === editingId && canEdit(task)))

  return <div className="crud-layout">
    {showForm && <section className="form-panel crud-form-panel">
      <div className="form-panel-heading"><div className="form-icon">T</div><div><h2>{editingId ? 'Modifier la tâche' : 'Nouvelle tâche'}</h2><p>Chaque tâche appartient à un projet.</p></div></div>
      <form className="organization-form" onSubmit={submit}><div className="form-grid">
        <div className="form-field full-width"><label htmlFor="task-project">Projet <span>*</span></label><select id="task-project" required value={form.projectId} onChange={event => setForm({ ...form, projectId: event.target.value })}><option value="">Choisir un projet</option>{projects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}</select></div>
        <div className="form-field full-width"><label htmlFor="task-name">Nom de la tâche <span>*</span></label><input id="task-name" required maxLength={160} value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} /></div>
        <div className="form-field full-width"><label htmlFor="task-status">Statut</label><select id="task-status" value={form.status} onChange={event => setForm({ ...form, status: event.target.value as TaskStatus })}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
        <div className="form-field"><label htmlFor="task-start">Date de début</label><input id="task-start" type="date" value={form.startDate ?? ''} onChange={event => setForm({ ...form, startDate: event.target.value })} /></div>
        <div className="form-field"><label htmlFor="task-end">Date de fin</label><input id="task-end" type="date" value={form.endDate ?? ''} onChange={event => setForm({ ...form, endDate: event.target.value })} /></div>
      </div>
      {error && <div className="alert form-alert" role="alert">{error}</div>}{projects.length === 0 && <p className="field-hint">Créez un projet avant d’ajouter une tâche.</p>}
      <div className="form-actions"><button className="primary-button" disabled={saving || projects.length === 0 || (!editingId && !canCreate(form.projectId))}>{saving ? 'Enregistrement…' : editingId ? 'Enregistrer' : 'Créer la tâche'}</button>{editingId && <button type="button" className="secondary-button" onClick={() => { setEditingId(null); setForm(emptyForm) }}>Annuler</button>}</div></form>
    </section>}
    <section className="crud-list-panel" aria-live="polite">
      {loading ? <div className="empty-state">Chargement des tâches…</div> : tasks.length ? tasks.map(task => <article className="crud-row" key={task.id}><div className="crud-symbol green">T</div><div className="crud-row-main"><strong>{task.name}</strong><small>{projectName(task.projectId)} · {statusLabels[task.status]}</small></div><div className="crud-actions">{canEdit(task) && <button type="button" className="secondary-button" disabled={saving} onClick={() => edit(task)}>Modifier</button>}{canManageResources(task) && <button type="button" className="secondary-button" disabled={saving} onClick={() => setResourcesTaskId(current => current === task.id ? null : task.id)}>Ressources</button>}{canDelete(task) && <button type="button" className="danger-button" disabled={saving} onClick={() => void remove(task)}>Supprimer</button>}</div></article>) : <div className="empty-state"><strong>Aucune tâche</strong><p>Choisissez un projet pour créer votre première tâche.</p></div>}
    </section>
    {resourcesTaskId && tasks.find(task => task.id === resourcesTaskId && canManageResources(task)) && projects.find(project => project.id === tasks.find(task => task.id === resourcesTaskId)?.projectId) && <TaskResourcesPanel key={`${resourcesTaskId}-${tasks.find(task => task.id === resourcesTaskId)?.status}-${tasks.find(task => task.id === resourcesTaskId)?.startDate}-${tasks.find(task => task.id === resourcesTaskId)?.endDate}`} task={tasks.find(task => task.id === resourcesTaskId)!} project={projects.find(project => project.id === tasks.find(task => task.id === resourcesTaskId)?.projectId)!} onClose={() => setResourcesTaskId(null)} />}
  </div>
}
