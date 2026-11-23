import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '../../api/http'
import { sprintService } from '../../api/sprintService'
import { taskService } from '../../api/taskService'
import { canProject } from '../../api/accessPermissions'
import type { EffectiveAccess, Project, Sprint, SprintRequest, Task, TaskStatus } from '../../api/types'
import './scrum.css'

const statusLabels: Record<TaskStatus, string> = { TODO: 'À faire', STARTED: 'En cours', BLOCKED: 'Bloquée', DONE: 'Terminée' }
const blankSprint = (projectId: string): SprintRequest => ({ projectId, name: '', goal: '', startDate: '', endDate: '', status: 'PLANNED' })

export function ScrumPlanning({ projects, tasks, access, onTasksChanged }: { projects: Project[]; tasks: Task[]; access: EffectiveAccess | null; onTasksChanged: () => void }) {
  const eligibleProjects = useMemo(() => projects.filter(project => canProject(access, project.id, project.spaceId, 'task.view.scrum')), [projects, access])
  const [projectId, setProjectId] = useState('')
  const [sprints, setSprints] = useState<Sprint[]>([])
  const [selectedSprintId, setSelectedSprintId] = useState('')
  const [form, setForm] = useState<SprintRequest>(blankSprint(''))
  const [creating, setCreating] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!eligibleProjects.some(project => project.id === projectId)) setProjectId(eligibleProjects[0]?.id ?? '')
  }, [eligibleProjects, projectId])
  useEffect(() => { setForm(blankSprint(projectId)); setCreating(false) }, [projectId])
  useEffect(() => {
    if (!projectId) { setSprints([]); setSelectedSprintId(''); return }
    let active = true
    sprintService.list(projectId).then(items => {
      if (!active) return
      setSprints(items)
      setSelectedSprintId(current => items.some(item => item.id === current) ? current : items.find(item => item.status === 'ACTIVE')?.id ?? items[0]?.id ?? '')
    }).catch(cause => { if (active) setError(getApiErrorMessage(cause, 'Impossible de charger les sprints.')) })
    return () => { active = false }
  }, [projectId])

  const project = eligibleProjects.find(item => item.id === projectId)
  const sprint = sprints.find(item => item.id === selectedSprintId)
  const mayManageSprints = !!project && canProject(access, project.id, project.spaceId, 'task.sprint.manage')
  const mayEditTasks = !!project && canProject(access, project.id, project.spaceId, 'task.edit')
  const projectTasks = tasks.filter(task => task.projectId === projectId)
  const backlog = projectTasks.filter(task => !task.sprintId)
  const sprintTasks = projectTasks.filter(task => task.sprintId === sprint?.id)

  async function reloadSprints(selectId?: string) {
    const items = await sprintService.list(projectId)
    setSprints(items)
    setSelectedSprintId(selectId && items.some(item => item.id === selectId) ? selectId : items.find(item => item.status === 'ACTIVE')?.id ?? items[0]?.id ?? '')
  }

  async function saveSprint(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      const saved = await sprintService.create(projectId, { ...form, projectId, startDate: form.startDate || null, endDate: form.endDate || null })
      await reloadSprints(saved.id); setCreating(false); setForm(blankSprint(projectId))
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de créer le sprint.')) }
    finally { setBusy(false) }
  }

  async function changeSprintStatus(next: Sprint['status']) {
    if (!sprint) return
    setBusy(true); setError('')
    try { await sprintService.update(sprint.id, { projectId, name: sprint.name, goal: sprint.goal, startDate: sprint.startDate, endDate: sprint.endDate, status: next }); await reloadSprints(sprint.id) }
    catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de modifier le sprint.')) }
    finally { setBusy(false) }
  }

  async function deleteSprint() {
    if (!sprint || !window.confirm(`Supprimer le sprint « ${sprint.name} » ? Ses tâches reviendront au backlog.`)) return
    setBusy(true); setError('')
    try { await sprintService.remove(sprint.id); await reloadSprints(); onTasksChanged() }
    catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de supprimer le sprint.')) }
    finally { setBusy(false) }
  }

  async function assign(task: Task, add: boolean) {
    if (!sprint) return
    setBusy(true); setError('')
    try {
      if (add) {
        if (task.sprintId) await sprintService.removeTask(task.sprintId, task.id)
        await sprintService.addTask(sprint.id, task.id)
      } else await sprintService.removeTask(sprint.id, task.id)
      onTasksChanged(); await reloadSprints(sprint.id)
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de déplacer la tâche.')) }
    finally { setBusy(false) }
  }

  async function setTaskStatus(task: Task, status: TaskStatus) {
    setBusy(true); setError('')
    try {
      await taskService.update(task.id, { projectId: task.projectId, name: task.name, status, priority: task.priority, estimatePoints: task.estimatePoints, dueDate: task.dueDate, baselineStart: task.baselineStart, baselineEnd: task.baselineEnd, startDate: task.startDate, endDate: task.endDate, progress: task.progress })
      onTasksChanged()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de modifier la tâche.')) }
    finally { setBusy(false) }
  }

  return <section className="scrum-planning">
    <header className="scrum-planning-header"><div><span className="task-workspace-eyebrow">PLANIFICATION SCRUM</span><h3>Sprints et backlog</h3><p>Planifiez un sprint daté, définissez son objectif et sélectionnez les tâches à engager.</p></div>
      <label>Projet<select value={projectId} onChange={event => setProjectId(event.target.value)}><option value="">Choisir un projet</option>{eligibleProjects.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
    </header>
    {!project ? <div className="empty-state">Aucun projet avec une vue Scrum accessible.</div> : <>
      {error && <div className="alert form-alert" role="alert">{error}</div>}
      <div className="scrum-sprint-toolbar"><label>Sprint<select value={selectedSprintId} onChange={event => setSelectedSprintId(event.target.value)}><option value="">Sélectionner un sprint</option>{sprints.map(item => <option key={item.id} value={item.id}>{item.name} · {item.status === 'ACTIVE' ? 'En cours' : item.status === 'COMPLETED' ? 'Terminé' : 'Planifié'}</option>)}</select></label>{mayManageSprints && <button type="button" className="primary-button" onClick={() => { setCreating(value => !value); setError('') }}>＋ Nouveau sprint</button>}</div>
      {creating && mayManageSprints && <form className="scrum-sprint-form" onSubmit={saveSprint}><label>Nom<input required maxLength={100} value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} placeholder="Sprint 1" /></label><label>Objectif<input maxLength={1000} value={form.goal ?? ''} onChange={event => setForm({ ...form, goal: event.target.value })} placeholder="Objectif du sprint" /></label><label>Début<input type="date" value={form.startDate ?? ''} onChange={event => setForm({ ...form, startDate: event.target.value })} /></label><label>Fin<input type="date" min={form.startDate || undefined} value={form.endDate ?? ''} onChange={event => setForm({ ...form, endDate: event.target.value })} /></label><button className="primary-button" disabled={busy}>{busy ? 'Création…' : 'Créer le sprint'}</button></form>}
      {sprint ? <><div className="scrum-sprint-summary"><div><div className="scrum-sprint-title"><h4>{sprint.name}</h4><span className={`scrum-status scrum-status-${sprint.status.toLowerCase()}`}>{sprint.status === 'ACTIVE' ? 'En cours' : sprint.status === 'COMPLETED' ? 'Terminé' : 'Planifié'}</span></div><p>{sprint.goal || 'Aucun objectif défini.'}</p><small>{sprint.startDate ?? 'Date à définir'} → {sprint.endDate ?? 'Date à définir'} · {sprint.completedTaskCount}/{sprint.taskCount} tâches terminées</small></div><div className="scrum-sprint-actions">{mayManageSprints && sprint.status === 'PLANNED' && <button className="secondary-button" disabled={busy} onClick={() => void changeSprintStatus('ACTIVE')}>Démarrer</button>}{mayManageSprints && sprint.status === 'ACTIVE' && <button className="secondary-button" disabled={busy} onClick={() => void changeSprintStatus('COMPLETED')}>Terminer</button>}{mayManageSprints && <button className="text-button" disabled={busy} onClick={() => void deleteSprint()}>Supprimer</button>}</div></div>
        <div className="scrum-planning-columns"><section><header><div><h4>Backlog</h4><small>{backlog.length} tâches disponibles</small></div></header>{backlog.map(task => <article className="scrum-task-card" key={task.id}><strong>{task.name}</strong><span>{statusLabels[task.status]}</span>{mayManageSprints && mayEditTasks && <button disabled={busy || sprint.status === 'COMPLETED'} onClick={() => void assign(task, true)}>Planifier →</button>}</article>)}{!backlog.length && <p className="scrum-empty">Le backlog est vide.</p>}</section>
          <section><header><div><h4>{sprint.status === 'ACTIVE' ? 'Sprint en cours' : 'Tâches du sprint'}</h4><small>{sprintTasks.length} tâches engagées</small></div></header>{sprintTasks.map(task => <article className="scrum-task-card" key={task.id}><strong>{task.name}</strong>{mayEditTasks ? <select aria-label={`Statut de ${task.name}`} value={task.status} disabled={busy || sprint.status === 'COMPLETED'} onChange={event => void setTaskStatus(task, event.target.value as TaskStatus)}>{Object.entries(statusLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select> : <span>{statusLabels[task.status]}</span>}{mayManageSprints && mayEditTasks && <button disabled={busy || sprint.status === 'COMPLETED'} onClick={() => void assign(task, false)}>← Backlog</button>}</article>)}{!sprintTasks.length && <p className="scrum-empty">Ajoutez des tâches depuis le backlog pour planifier le sprint.</p>}</section></div>
      </> : !creating && <div className="empty-state">Créez ou sélectionnez un sprint pour organiser son backlog.</div>}
    </>}
  </section>
}
