import { useEffect, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '../../api/http'
import { projectService } from '../../api/projectService'
import { taskService } from '../../api/taskService'
import { TaskResourcesPanel } from './TaskResourcesPanel'
import { ScrumPlanning } from './ScrumPlanning'
import { TaskCommentsPanel } from './TaskCommentsPanel'
import type { Project, Task, TaskRequest, TaskStatus } from '../../api/types'
import type { EffectiveAccess } from '../../api/types'
import { canProject } from '../../api/accessPermissions'

const emptyForm: TaskRequest = { projectId: '', name: '', status: 'TODO', priority: 'NORMAL', estimatePoints: null, dueDate: '', baselineStart: '', baselineEnd: '', startDate: '', endDate: '', progress: 0 }
const formatDate = (value: string | null | undefined) => value ? new Date(`${value}T00:00:00`).toLocaleDateString('fr-FR') : null
const statusLabels: Record<TaskStatus, string> = { TODO: 'À faire', STARTED: 'En cours', BLOCKED: 'Bloquée', DONE: 'Terminée' }
const statusOrder: TaskStatus[] = ['TODO', 'STARTED', 'BLOCKED', 'DONE']
type TaskViewMode = 'LIST' | 'KANBAN' | 'SCRUM'

export function TasksManager({ onChange, access }: { onChange: () => void; access: EffectiveAccess | null }) {
  const [projects, setProjects] = useState<Project[]>([])
  const [tasks, setTasks] = useState<Task[]>([])
  const [form, setForm] = useState<TaskRequest>(emptyForm)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [resourcesTaskId, setResourcesTaskId] = useState<string | null>(null)
  const [commentsTaskId, setCommentsTaskId] = useState<string | null>(null)
  const [selectedViewMode, setViewMode] = useState<TaskViewMode>('LIST')
  const [showTaskForm, setShowTaskForm] = useState(false)
  const [draggedTaskId, setDraggedTaskId] = useState<string | null>(null)
  const [dragOverStatus, setDragOverStatus] = useState<TaskStatus | null>(null)

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
      const payload: TaskRequest = { ...form, baselineStart: form.baselineStart || null, baselineEnd: form.baselineEnd || null, dueDate: form.dueDate || null,
        startDate: form.startDate || null, endDate: form.endDate || null, progress: form.progress ?? 0 }
      const saved = editingId ? await taskService.update(editingId, payload) : await taskService.create(payload)
      setTasks(current => editingId ? current.map(item => item.id === saved.id ? saved : item) : [...current, saved].sort((a, b) => a.name.localeCompare(b.name)))
      // Keep the project selected so the next task can be created right away.
      setForm(editingId ? emptyForm : { ...emptyForm, projectId: form.projectId }); setEditingId(null); setShowTaskForm(false); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’enregistrer cette tâche.')) }
    finally { setSaving(false) }
  }

  function edit(task: Task) {
    setForm({ projectId: task.projectId, name: task.name, status: task.status, priority: task.priority, estimatePoints: task.estimatePoints, dueDate: task.dueDate ?? '', baselineStart: task.baselineStart ?? '', baselineEnd: task.baselineEnd ?? '', startDate: task.startDate ?? '', endDate: task.endDate ?? '', progress: task.progress })
    setEditingId(task.id); setShowTaskForm(true); setError('')
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

  const selectedProject = projects.find(project => project.id === form.projectId)
  const today = new Date().toISOString().slice(0, 10)
  const projectName = (id: string) => projects.find(project => project.id === id)?.name ?? 'Projet indisponible'
  const canCreate = (projectId: string) => { const project = projects.find(item => item.id === projectId); return !!project && canProject(access, project.id, project.spaceId, 'task.create') }
  const canEdit = (task: Task) => { const project = projects.find(item => item.id === task.projectId); return !!project && canProject(access, project.id, project.spaceId, 'task.edit') }
  const canDelete = (task: Task) => { const project = projects.find(item => item.id === task.projectId); return !!project && canProject(access, project.id, project.spaceId, 'task.delete') }
  const canManageResources = (task: Task) => { const project = projects.find(item => item.id === task.projectId); return !!project && canProject(access, project.id, project.spaceId, 'project.resources.manage') }
  const canComment = (task: Task) => { const project = projects.find(item => item.id === task.projectId); return !!project && canProject(access, project.id, project.spaceId, 'task.comment') }
  const showForm = projects.some(project => canProject(access, project.id, project.spaceId, 'task.create')) || (!!editingId && tasks.some(task => task.id === editingId && canEdit(task)))
  const listPermissionKeys = ['task.view', 'task.edit', 'task.delete', 'task.comment', 'project.resources.manage']
  const canViewList = projects.some(project => listPermissionKeys.some(key => canProject(access, project.id, project.spaceId, key)))
  const canViewKanban = projects.some(project => canProject(access, project.id, project.spaceId, 'task.view.kanban'))
  const canViewScrum = projects.some(project => canProject(access, project.id, project.spaceId, 'task.view.scrum'))
  const taskViews: { id: TaskViewMode; label: string; enabled: boolean }[] = [
    { id: 'LIST', label: 'Liste', enabled: canViewList }, { id: 'KANBAN', label: 'Kanban', enabled: canViewKanban }, { id: 'SCRUM', label: 'Scrum', enabled: canViewScrum },
  ]
  // A view the user cannot open falls back to the first one they can.
  const selectedViewAvailable = selectedViewMode === 'LIST' ? canViewList : selectedViewMode === 'KANBAN' ? canViewKanban : canViewScrum
  const viewMode: TaskViewMode = selectedViewAvailable ? selectedViewMode
    : canViewList ? 'LIST' : canViewKanban ? 'KANBAN' : canViewScrum ? 'SCRUM' : selectedViewMode
  const canViewTask = (task: Task, mode = viewMode) => {
    const project = projects.find(item => item.id === task.projectId)
    if (!project) return false
    const keys = mode === 'KANBAN' ? ['task.view.kanban'] : mode === 'SCRUM' ? ['task.view.scrum'] : listPermissionKeys
    return keys.some(key => canProject(access, project.id, project.spaceId, key))
  }
  const tasksForView = tasks.filter(task => canViewTask(task))

  async function changeTaskStatus(task: Task, status: TaskStatus) {
    if (!canEdit(task) || task.status === status) return
    setSaving(true); setError('')
    try {
      const saved = await taskService.update(task.id, { projectId: task.projectId, name: task.name, status, priority: task.priority, estimatePoints: task.estimatePoints, dueDate: task.dueDate, baselineStart: task.baselineStart, baselineEnd: task.baselineEnd, startDate: task.startDate, endDate: task.endDate, progress: task.progress })
      setTasks(current => current.map(item => item.id === saved.id ? saved : item)); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de modifier le statut de cette tâche.')) }
    finally { setSaving(false) }
  }

  function renderBoardCard(task: Task) {
    return <article className="task-board-card" key={task.id} draggable={canEdit(task) && !saving} onDragStart={() => setDraggedTaskId(task.id)} onDragEnd={() => { setDraggedTaskId(null); setDragOverStatus(null) }}>
      <div className="task-card-topline"><span className="task-card-grip" aria-hidden="true">⠿</span><span className={`task-card-priority task-card-priority-${task.status.toLowerCase()}`}>{statusLabels[task.status]}</span></div>
      <strong>{task.name}</strong><small className="task-card-project">{projectName(task.projectId)}</small>
      {(task.baselineStart || task.baselineEnd) && <small className="task-card-dates">Planifié : {formatDate(task.baselineStart) ?? '—'} → {formatDate(task.baselineEnd) ?? '—'}</small>}
      {canComment(task) && <button type="button" className="task-card-edit" onClick={() => setCommentsTaskId(task.id)}>Discussion</button>}
      {canEdit(task) && <div className="task-board-card-actions"><button type="button" className="task-card-edit" disabled={saving} onClick={() => edit(task)}>Modifier</button><select className="task-card-status-select" aria-label={`Changer le statut de ${task.name}`} disabled={saving} value={task.status} onChange={event => void changeTaskStatus(task, event.target.value as TaskStatus)}>{statusOrder.map(status => <option key={status} value={status}>{statusLabels[status]}</option>)}</select>{viewMode === 'SCRUM' && task.status === 'TODO' && <button type="button" className="task-card-start" disabled={saving} onClick={() => void changeTaskStatus(task, 'STARTED')}>Ajouter au sprint</button>}</div>}
    </article>
  }

  return <div className="crud-layout">
    <header className="task-workspace-header"><div className="task-workspace-title"><span className="task-workspace-eyebrow">ESPACE DE TRAVAIL</span><h2>Tâches</h2><p>Organisez le travail et suivez son avancement.</p></div><div className="task-workspace-tools">{taskViews.some(view => view.enabled) && <div className="task-view-tabs" role="tablist" aria-label="Vues des tâches">{taskViews.filter(view => view.enabled).map(view => <button key={view.id} type="button" role="tab" aria-selected={viewMode === view.id} className={viewMode === view.id ? 'active' : ''} onClick={() => setViewMode(view.id)}><span aria-hidden="true">{view.id === 'LIST' ? '☷' : view.id === 'KANBAN' ? '▥' : '◫'}</span>{view.label}</button>)}</div>}{showForm && <button type="button" className="task-create-button" onClick={() => { setEditingId(null); setForm({ ...emptyForm, projectId: projects.find(project => canCreate(project.id))?.id ?? '' }); setShowTaskForm(true); setError('') }}>＋ Nouvelle tâche</button>}</div></header>
    {showTaskForm && <div className="task-modal-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) { setShowTaskForm(false); setEditingId(null) } }}><section className="form-panel crud-form-panel task-modal" role="dialog" aria-modal="true" aria-labelledby="task-form-title">
      <div className="form-panel-heading"><div className="form-icon">T</div><div><h2 id="task-form-title">{editingId ? 'Modifier la tâche' : 'Nouvelle tâche'}</h2><p>Chaque tâche appartient à un projet.</p></div><button type="button" className="task-modal-close" aria-label="Fermer" onClick={() => { setShowTaskForm(false); setEditingId(null) }}>×</button></div>
      <form className="organization-form" onSubmit={submit}><div className="form-grid">
        <div className="form-field full-width"><label htmlFor="task-project">Projet <span>*</span></label><select id="task-project" required value={form.projectId} onChange={event => setForm({ ...form, projectId: event.target.value })}><option value="">Choisir un projet</option>{projects.map(project => <option key={project.id} value={project.id}>{project.name}</option>)}</select></div>
        <div className="form-field full-width"><label htmlFor="task-name">Nom de la tâche <span>*</span></label><input id="task-name" required maxLength={160} value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} /></div>
        <div className="form-field full-width"><label htmlFor="task-status">Statut</label><select id="task-status" value={form.status} onChange={event => setForm({ ...form, status: event.target.value as TaskStatus })}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
        <div className="form-field"><label htmlFor="task-priority">Priorité</label><select id="task-priority" value={form.priority} onChange={event => setForm({ ...form, priority: event.target.value as TaskRequest['priority'] })}><option value="LOW">Basse</option><option value="NORMAL">Normale</option><option value="HIGH">Haute</option><option value="URGENT">Urgente</option></select></div>
        <div className="form-field"><label htmlFor="task-estimate">Estimation (points)</label><input id="task-estimate" type="number" min="0" max="1000" value={form.estimatePoints ?? ''} onChange={event => setForm({ ...form, estimatePoints: event.target.value === '' ? null : Number(event.target.value) })} /></div>
        <div className="form-field"><label htmlFor="task-due-date">Échéance</label><input id="task-due-date" type="date" value={form.dueDate ?? ''} onChange={event => setForm({ ...form, dueDate: event.target.value })} /></div>
        <div className="form-field"><label htmlFor="task-planned-start">Début planifié</label><input id="task-planned-start" type="date" min={selectedProject?.baselineStart ?? undefined} max={selectedProject?.baselineEnd ?? undefined} value={form.baselineStart ?? ''} onChange={event => setForm({ ...form, baselineStart: event.target.value })} /></div>
        <div className="form-field"><label htmlFor="task-planned-end">Fin planifiée</label><input id="task-planned-end" type="date" min={form.baselineStart || selectedProject?.baselineStart || undefined} max={selectedProject?.baselineEnd ?? undefined} value={form.baselineEnd ?? ''} onChange={event => setForm({ ...form, baselineEnd: event.target.value })} /></div>
        {form.status !== 'TODO' && <div className="form-field"><label htmlFor="task-actual-start">Début réel</label><input id="task-actual-start" type="date" max={today} value={form.startDate ?? ''} onChange={event => setForm({ ...form, startDate: event.target.value })} /><small className="field-hint">Vide : aujourd’hui au passage « En cours ».</small></div>}
        {form.status === 'DONE' && <div className="form-field"><label htmlFor="task-actual-end">Fin réelle</label><input id="task-actual-end" type="date" min={form.startDate || undefined} max={today} value={form.endDate ?? ''} onChange={event => setForm({ ...form, endDate: event.target.value })} /><small className="field-hint">Vide : aujourd’hui.</small></div>}
        {(form.status === 'STARTED' || form.status === 'BLOCKED') && <div className="form-field full-width"><label htmlFor="task-progress">Avancement : {form.progress ?? 0} %</label><input id="task-progress" type="range" min={0} max={99} step={5} value={form.progress ?? 0} onChange={event => setForm({ ...form, progress: Number(event.target.value) })} /></div>}
      </div>
      {error && <div className="alert form-alert" role="alert">{error}</div>}{projects.length === 0 && <p className="field-hint">Créez un projet avant d’ajouter une tâche.</p>}
      <div className="form-actions"><button className="primary-button" aria-busy={saving} disabled={saving || projects.length === 0 || (!editingId && !canCreate(form.projectId))}>{saving ? 'Enregistrement…' : editingId ? 'Enregistrer' : 'Créer la tâche'}</button><button type="button" className="secondary-button" onClick={() => { setShowTaskForm(false); setEditingId(null); setForm(emptyForm) }}>Annuler</button></div></form>
    </section></div>}
    {viewMode === 'LIST' && canViewList && <section className="crud-list-panel" aria-live="polite">
      {loading ? <div className="empty-state">Chargement des tâches…</div> : tasksForView.length ? tasksForView.map(task => <article className="crud-row" key={task.id}><div className="crud-symbol green">T</div><div className="crud-row-main"><strong>{task.name}</strong><small>{projectName(task.projectId)} · {statusLabels[task.status]}{task.status === 'STARTED' || task.status === 'BLOCKED' ? ` (${task.progress} %)` : ''} · Priorité {task.priority}{task.estimatePoints != null ? ` · ${task.estimatePoints} pts` : ''}{task.dueDate ? ` · Échéance ${formatDate(task.dueDate)}` : ''}{task.baselineStart || task.baselineEnd ? ` · Planifié : ${formatDate(task.baselineStart) ?? '?'} → ${formatDate(task.baselineEnd) ?? '?'}` : ''}{task.startDate ? ` · Réel : ${formatDate(task.startDate)} → ${formatDate(task.endDate) ?? 'en cours'}` : ''}</small></div><div className="crud-actions">{canComment(task) && <button type="button" className="secondary-button" onClick={() => setCommentsTaskId(task.id)}>Discussion</button>}{canEdit(task) && <button type="button" className="secondary-button" disabled={saving} onClick={() => edit(task)}>Modifier</button>}{canManageResources(task) && <button type="button" className="secondary-button" disabled={saving} onClick={() => setResourcesTaskId(current => current === task.id ? null : task.id)}>Ressources</button>}{canDelete(task) && <button type="button" className="danger-button" disabled={saving} onClick={() => void remove(task)}>Supprimer</button>}</div></article>) : <div className="empty-state"><strong>Aucune tâche</strong><p>Choisissez un projet pour créer votre première tâche.</p></div>}
    </section>}
    {viewMode === 'KANBAN' && canViewKanban && <section className="task-board task-kanban-board" aria-label="Tableau Kanban">
      {loading ? <div className="empty-state">Chargement...</div> : statusOrder.map(status => { const columnTasks = tasksForView.filter(task => task.status === status); return <section className={`task-board-column task-column-${status.toLowerCase()} ${dragOverStatus === status ? 'task-column-over' : ''}`} key={status} onDragOver={event => { if (draggedTaskId) { event.preventDefault(); setDragOverStatus(status) } }} onDragLeave={() => setDragOverStatus(current => current === status ? null : current)} onDrop={event => { event.preventDefault(); const task = tasks.find(item => item.id === draggedTaskId); setDraggedTaskId(null); setDragOverStatus(null); if (task) void changeTaskStatus(task, status) }}><header><div><span className="task-column-dot"/><strong>{statusLabels[status]}</strong></div><span>{columnTasks.length}</span></header><div className="task-column-cards">{columnTasks.length ? columnTasks.map(renderBoardCard) : <p className="task-board-empty">Déposez une tâche ici</p>}</div></section> })}
    </section>}
    {viewMode === 'SCRUM' && canViewScrum && <ScrumPlanning projects={projects} tasks={tasks.filter(task => canViewTask(task, 'SCRUM'))} access={access} onTasksChanged={() => { void taskService.list().then(setTasks).catch(cause => setError(getApiErrorMessage(cause, 'Impossible de recharger les tâches.'))); onChange() }} />}
    {commentsTaskId && tasks.find(task => task.id === commentsTaskId && canComment(task)) && <TaskCommentsPanel task={tasks.find(task => task.id === commentsTaskId)!} canComment={canComment(tasks.find(task => task.id === commentsTaskId)!)} onClose={() => setCommentsTaskId(null)} />}
    {resourcesTaskId && tasks.find(task => task.id === resourcesTaskId && canManageResources(task)) && projects.find(project => project.id === tasks.find(task => task.id === resourcesTaskId)?.projectId) && <TaskResourcesPanel key={`${resourcesTaskId}-${tasks.find(task => task.id === resourcesTaskId)?.status}-${tasks.find(task => task.id === resourcesTaskId)?.startDate}-${tasks.find(task => task.id === resourcesTaskId)?.endDate}`} task={tasks.find(task => task.id === resourcesTaskId)!} project={projects.find(project => project.id === tasks.find(task => task.id === resourcesTaskId)?.projectId)!} onClose={() => setResourcesTaskId(null)} />}
  </div>
}
