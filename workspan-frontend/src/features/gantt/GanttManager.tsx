import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { canProject } from '../../api/accessPermissions'
import { ganttService } from '../../api/ganttService'
import { getApiErrorMessage } from '../../api/http'
import { projectService } from '../../api/projectService'
import type { DependencyType, EffectiveAccess, Gantt, GanttHealth, GanttLink, GanttScheduleState, GanttTaskRow, Project, TaskStatus } from '../../api/types'
import './gantt.css'

/*
 * Display-only Gantt chart. Every value shown (dates, margins, variances, critical path, bar positions in days)
 * comes from GET /api/gantt/projects/{id}. The component only converts "days" into pixels.
 */

type Scale = 'day' | 'week' | 'month'
const PX_PER_DAY: Record<Scale, number> = { day: 32, week: 14, month: 5 }
const ROW_HEIGHT = 46
const HEADER_HEIGHT = 46

const statusLabels: Record<TaskStatus, string> = { TODO: 'À faire', STARTED: 'En cours', BLOCKED: 'Bloquée', DONE: 'Terminée' }
const stateLabels: Record<GanttScheduleState, string> = {
  COMPLETED_ON_TIME: 'Terminée à temps', COMPLETED_LATE: 'Terminée en retard', UNSCHEDULED: 'Non planifiée', BLOCKED: 'Bloquée',
  LATE: 'En retard', NOT_STARTED: 'Pas démarrée', BEHIND: 'En retard sur l’avancement', AHEAD: 'En avance', ON_TRACK: 'Dans les temps',
}
const healthLabels: Record<GanttHealth, string> = {
  NO_TASKS: 'Aucune tâche', ON_TRACK: 'Dans les temps', AT_RISK: 'À risque', LATE: 'En retard', COMPLETED: 'Terminé', COMPLETED_LATE: 'Terminé en retard',
}
const dependencyLabels: Record<DependencyType, string> = {
  FINISH_TO_START: 'Fin → Début (FD)', START_TO_START: 'Début → Début (DD)', FINISH_TO_FINISH: 'Fin → Fin (FF)', START_TO_FINISH: 'Début → Fin (DF)',
}
const dependencyShort: Record<DependencyType, string> = { FINISH_TO_START: 'FD', START_TO_START: 'DD', FINISH_TO_FINISH: 'FF', START_TO_FINISH: 'DF' }

const toDate = (iso: string) => new Date(`${iso}T00:00:00`)
const fmt = (value: string | null | undefined) => value ? toDate(value).toLocaleDateString('fr-FR') : '—'
const days = (value: number | null | undefined, signed = false) => value === null || value === undefined ? '—' : `${signed && value > 0 ? '+' : ''}${value} j`
const varianceClass = (value: number | null | undefined) => value === null || value === undefined || value === 0 ? '' : value > 0 ? 'gantt-negative' : 'gantt-positive'
const toInput = (value: string | null) => value ?? ''
const orNull = (value: string) => value || null

type ScheduleDraft = { baselineStart: string; baselineEnd: string; startDate: string; endDate: string; progress: number }
type DependencyDraft = { predecessorTaskId: string; successorTaskId: string; type: DependencyType; lag: string }
const emptyDependency: DependencyDraft = { predecessorTaskId: '', successorTaskId: '', type: 'FINISH_TO_START', lag: '0' }

export function GanttManager({ access }: { access: EffectiveAccess | null }) {
  const [projects, setProjects] = useState<Project[]>([])
  const [chosenProjectId, setChosenProjectId] = useState('')
  const [gantt, setGantt] = useState<Gantt | null>(null)
  const [scale, setScale] = useState<Scale>('day')
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null)
  const [dependencyDraft, setDependencyDraft] = useState<DependencyDraft>(emptyDependency)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const visibleProjects = useMemo(() => projects.filter(project => canProject(access, project.id, project.spaceId, 'gantt.view')), [projects, access])
  const projectId = visibleProjects.some(item => item.id === chosenProjectId) ? chosenProjectId : visibleProjects[0]?.id ?? ''
  const project = visibleProjects.find(item => item.id === projectId) ?? null
  const canManageDependencies = !!project && canProject(access, project.id, project.spaceId, 'gantt.dependencies.manage')
  const canManageSchedule = !!project && canProject(access, project.id, project.spaceId, 'gantt.schedule.manage')

  useEffect(() => {
    const controller = new AbortController()
    projectService.list({ signal: controller.signal }).then(setProjects)
      .catch(cause => { if (!controller.signal.aborted) setError(getApiErrorMessage(cause, 'Impossible de charger les projets.')) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [])

  const load = useCallback(async (id: string, signal?: AbortSignal) => {
    setLoading(true); setError('')
    try { setGantt(await ganttService.project(id, { signal })) }
    catch (cause) { if (!signal?.aborted) { setGantt(null); setError(getApiErrorMessage(cause, 'Impossible de charger le diagramme de Gantt.')) } }
    finally { if (!signal?.aborted) setLoading(false) }
  }, [])

  useEffect(() => {
    if (!projectId) return
    const controller = new AbortController()
    ganttService.project(projectId, { signal: controller.signal })
      .then(data => { setGantt(data); setError('') })
      .catch(cause => { if (!controller.signal.aborted) { setGantt(null); setError(getApiErrorMessage(cause, 'Impossible de charger le diagramme de Gantt.')) } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [projectId])

  const selectedTask = gantt?.tasks.find(task => task.id === selectedTaskId) ?? null

  function changeProject(id: string) {
    if (id !== projectId) setLoading(true)
    setChosenProjectId(id); setSelectedTaskId(null); setNotice(''); setError('')
  }

  async function run(action: () => Promise<void>, fallback: string) {
    if (!projectId) return
    setSaving(true); setError(''); setNotice('')
    try { await action(); await load(projectId) }
    catch (cause) { setError(getApiErrorMessage(cause, fallback)) }
    finally { setSaving(false) }
  }

  const reschedule = () => {
    if (!window.confirm('Décaler les dates planifiées des tâches non démarrées pour respecter les dépendances ?')) return
    void run(async () => {
      const result = await ganttService.reschedule(projectId)
      setNotice(result.movedTasks ? `${result.movedTasks} tâche(s) replanifiée(s) : ${result.moves.map(move => `${move.name} (${fmt(move.newStart)} → ${fmt(move.newEnd)})`).join(', ')}` : 'Aucune tâche à replanifier : la planification respecte déjà les dépendances.')
    }, 'Replanification impossible.')
  }

  const saveSchedule = (scheduleDraft: ScheduleDraft) => {
    if (!selectedTask) return
    void run(async () => {
      await ganttService.updateSchedule(selectedTask.id, {
        baselineStart: orNull(scheduleDraft.baselineStart), baselineEnd: orNull(scheduleDraft.baselineEnd),
        startDate: orNull(scheduleDraft.startDate), endDate: orNull(scheduleDraft.endDate), progress: scheduleDraft.progress,
      })
      setNotice(`Planification de « ${selectedTask.name} » enregistrée.`)
    }, 'Impossible d’enregistrer la planification de cette tâche.')
  }

  const addDependency = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void run(async () => {
      await ganttService.createDependency(projectId, { ...dependencyDraft, lag: Number(dependencyDraft.lag) || 0 })
      setDependencyDraft(emptyDependency)
    }, 'Impossible de créer cette dépendance.')
  }

  const updateDependency = (link: GanttLink, changes: Partial<{ type: DependencyType; lag: number }>) => void run(async () => {
    await ganttService.updateDependency(link.id, { predecessorTaskId: link.predecessorTaskId, successorTaskId: link.successorTaskId, type: changes.type ?? link.type, lag: changes.lag ?? link.lag })
  }, 'Impossible de modifier cette dépendance.')

  const removeDependency = (link: GanttLink) => {
    if (!window.confirm('Supprimer cette dépendance ?')) return
    void run(() => ganttService.removeDependency(link.id), 'Impossible de supprimer cette dépendance.')
  }

  if (!loading && !visibleProjects.length) return <div className="empty-state"><strong>Aucun projet disponible</strong><p>Vous n’avez la permission « Consulter le diagramme de Gantt » sur aucun projet.</p></div>

  const taskName = (id: string) => gantt?.tasks.find(task => task.id === id)?.name ?? '—'

  return <div className="gantt-manager">
    <div className="crud-heading gantt-heading">
      <div><p className="eyebrow">PLANIFICATION</p><h2>Diagramme de Gantt</h2><p>Dates planifiées et réelles, marges, chemin critique et écarts. Tous les calculs sont effectués par le serveur.</p></div>
      <div className="gantt-toolbar">
        <div className="form-field"><label htmlFor="gantt-project">Projet</label><select id="gantt-project" value={projectId} onChange={event => changeProject(event.target.value)}>{visibleProjects.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}</select></div>
        <div className="gantt-scale" role="group" aria-label="Échelle">{(['day', 'week', 'month'] as Scale[]).map(item => <button key={item} type="button" className={scale === item ? 'active' : ''} aria-pressed={scale === item} onClick={() => setScale(item)}>{item === 'day' ? 'Jour' : item === 'week' ? 'Semaine' : 'Mois'}</button>)}</div>
        <button type="button" className="secondary-button" disabled={loading || saving || !projectId} onClick={() => void load(projectId)}>Actualiser</button>
        {canManageSchedule && <button type="button" className="primary-button" disabled={loading || saving || !gantt?.tasks.length} onClick={reschedule}>Replanifier</button>}
      </div>
    </div>

    {error && <div className="alert crud-alert" role="alert">{error}</div>}
    {notice && <div className="gantt-notice" role="status">{notice}</div>}

    {loading && !gantt ? <div className="empty-state">Chargement du diagramme…</div> : gantt && <>
      <Summary gantt={gantt} />
      {gantt.tasks.length === 0 ? <div className="empty-state"><strong>Aucune tâche dans ce projet</strong><p>Ajoutez des tâches avec des dates planifiées pour construire le diagramme.</p></div> : <>
        <Chart gantt={gantt} pxPerDay={PX_PER_DAY[scale]} scale={scale} selectedTaskId={selectedTaskId} onSelect={id => setSelectedTaskId(current => current === id ? null : id)} />
        <Legend />

        {selectedTask && <TaskDetails key={[selectedTask.id, selectedTask.plannedStart, selectedTask.plannedEnd, selectedTask.actualStart, selectedTask.actualEnd, selectedTask.progress].join('|')}
          task={selectedTask} gantt={gantt} canEdit={canManageSchedule} saving={saving} onSubmit={saveSchedule} onClose={() => setSelectedTaskId(null)} />}

        <section className="crud-list-panel gantt-table-panel">
          <div className="section-heading"><div><h2>Analyse des tâches</h2><p>Marge totale : retard possible sans décaler la fin du projet. Marge libre : retard possible sans décaler la tâche suivante.</p></div></div>
          <div className="gantt-table-scroll"><table className="gantt-table">
            <thead><tr><th>Tâche</th><th>Planifié</th><th>Réel</th><th>Prévision</th><th>Durée</th><th>Marge totale</th><th>Marge libre</th><th>Écart début</th><th>Écart fin</th><th>Avancement</th><th>État</th></tr></thead>
            <tbody>{gantt.tasks.map(task => <tr key={task.id} className={task.id === selectedTaskId ? 'selected' : ''} onClick={() => setSelectedTaskId(task.id)}>
              <td><strong>{task.critical && <span className="gantt-critical-dot" title="Chemin critique" />}{task.name}</strong><small>{statusLabels[task.status]}</small></td>
              <td>{fmt(task.plannedStart)}<small>→ {fmt(task.plannedEnd)}</small></td>
              <td>{fmt(task.actualStart)}<small>→ {task.actualStart && !task.actualEnd ? 'en cours' : fmt(task.actualEnd)}</small></td>
              <td>{fmt(task.forecastStart)}<small>→ {fmt(task.forecastEnd)}</small></td>
              <td>{days(task.plannedDurationDays)}</td>
              <td className={task.critical ? 'gantt-negative' : ''}>{days(task.totalFloatDays)}</td>
              <td>{days(task.freeFloatDays)}</td>
              <td className={varianceClass(task.startVarianceDays)}>{days(task.startVarianceDays, true)}</td>
              <td className={varianceClass(task.finishVarianceDays)}>{days(task.finishVarianceDays, true)}</td>
              <td>{task.progress} %<small>attendu {task.expectedProgress} %</small></td>
              <td><span className={`gantt-state gantt-state-${task.scheduleState.toLowerCase()}`}>{stateLabels[task.scheduleState]}</span></td>
            </tr>)}</tbody>
          </table></div>
        </section>

        <section className="crud-list-panel gantt-dependencies-panel">
          <div className="section-heading"><div><h2>Dépendances</h2><p>Le décalage (en jours calendaires) s’ajoute à la contrainte ; une valeur négative est une avance.</p></div><span className="count-pill">{gantt.dependencies.length}</span></div>
          {canManageDependencies && <form className="gantt-dependency-form" onSubmit={addDependency}>
            <div className="form-field"><label htmlFor="dep-pred">Tâche précédente</label><select id="dep-pred" required value={dependencyDraft.predecessorTaskId} onChange={event => setDependencyDraft({ ...dependencyDraft, predecessorTaskId: event.target.value })}><option value="">Choisir</option>{gantt.tasks.map(task => <option key={task.id} value={task.id}>{task.name}</option>)}</select></div>
            <div className="form-field"><label htmlFor="dep-type">Type</label><select id="dep-type" value={dependencyDraft.type} onChange={event => setDependencyDraft({ ...dependencyDraft, type: event.target.value as DependencyType })}>{Object.entries(dependencyLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
            <div className="form-field"><label htmlFor="dep-succ">Tâche suivante</label><select id="dep-succ" required value={dependencyDraft.successorTaskId} onChange={event => setDependencyDraft({ ...dependencyDraft, successorTaskId: event.target.value })}><option value="">Choisir</option>{gantt.tasks.filter(task => task.id !== dependencyDraft.predecessorTaskId).map(task => <option key={task.id} value={task.id}>{task.name}</option>)}</select></div>
            <div className="form-field gantt-lag-field"><label htmlFor="dep-lag">Décalage (j)</label><input id="dep-lag" type="number" min={-365} max={365} step={1} value={dependencyDraft.lag} onChange={event => setDependencyDraft({ ...dependencyDraft, lag: event.target.value })} /></div>
            <button className="primary-button" disabled={saving}>Ajouter</button>
          </form>}
          {gantt.dependencies.length ? gantt.dependencies.map(link => <article key={link.id} className={`crud-row gantt-dependency-row ${link.violated ? 'violated' : ''}`}>
            <div className={`crud-symbol ${link.critical ? 'gantt-symbol-critical' : 'violet'}`}>{dependencyShort[link.type]}</div>
            <div className="crud-row-main"><strong>{taskName(link.predecessorTaskId)} → {taskName(link.successorTaskId)}</strong>
              <small>{dependencyLabels[link.type]} · décalage {link.lag} j{link.critical ? ' · chemin critique' : ''}</small>
              {link.violation && <small className="gantt-negative">⚠ {link.violation}</small>}</div>
            {canManageDependencies && <div className="crud-actions">
              <select className="select-compact" aria-label="Type de dépendance" value={link.type} disabled={saving} onChange={event => updateDependency(link, { type: event.target.value as DependencyType })}>{Object.entries(dependencyLabels).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>
              <input key={`${link.id}-${link.lag}`} aria-label="Décalage en jours" className="gantt-lag-input" type="number" min={-365} max={365} defaultValue={link.lag} disabled={saving} onBlur={event => { const lag = Number(event.target.value) || 0; if (lag !== link.lag) updateDependency(link, { lag }) }} />
              <button type="button" className="danger-button" disabled={saving} onClick={() => removeDependency(link)}>Supprimer</button>
            </div>}
          </article>) : <div className="empty-state"><p>Aucune dépendance entre les tâches de ce projet.</p></div>}
        </section>
      </>}
    </>}
  </div>
}

function Summary({ gantt }: { gantt: Gantt }) {
  const p = gantt.project
  return <section className="gantt-kpis" aria-label="Analyse du projet">
    <article className={`gantt-health gantt-health-${p.health.toLowerCase()}`}><span>Santé du projet</span><strong>{healthLabels[p.health]}</strong><small>{p.dependencyViolations ? `${p.dependencyViolations} dépendance(s) non respectée(s)` : 'Dépendances respectées'}</small></article>
    <article><span>Avancement</span><strong>{p.progress} %</strong><div className="gantt-progress-track" aria-hidden><span style={{ width: `${p.progress}%` }} /><i style={{ left: `${p.expectedProgress}%` }} /></div><small>Attendu à ce jour : {p.expectedProgress} %{p.schedulePerformanceIndex !== null ? ` · indice ${p.schedulePerformanceIndex.toFixed(2)}` : ''}</small></article>
    <article><span>Fin planifiée → prévue</span><strong>{fmt(p.forecastEnd)}</strong><small>Planifiée : {fmt(p.plannedEnd)}{p.actualEnd ? ` · réelle : ${fmt(p.actualEnd)}` : ''}</small></article>
    <article><span>Marge du projet</span><strong className={varianceClass(p.finishVarianceDays)}>{days(p.projectMarginDays)}</strong><small>{p.finishVarianceDays !== null && p.finishVarianceDays > 0 ? `${p.finishVarianceDays} j de retard sur la fin planifiée` : 'Avant la date de fin planifiée'}</small></article>
    <article><span>Dates</span><strong>{fmt(p.plannedStart)}</strong><small>Début planifié · réel : {fmt(p.actualStart)} · durée {days(p.plannedDurationDays)}</small></article>
    <article><span>Tâches</span><strong>{p.completedTasks}/{p.taskCount}</strong><small>{p.lateTasks} en retard · {p.criticalTasks} critique(s){p.unscheduledTasks ? ` · ${p.unscheduledTasks} non planifiée(s)` : ''}</small></article>
  </section>
}

function Chart({ gantt, pxPerDay, scale, selectedTaskId, onSelect }: { gantt: Gantt; pxPerDay: number; scale: Scale; selectedTaskId: string | null; onSelect: (id: string) => void }) {
  const { timeline, tasks, dependencies } = gantt
  const width = timeline.totalDays * pxPerDay
  const height = tasks.length * ROW_HEIGHT
  const rowIndex = useMemo(() => new Map(tasks.map((task, index) => [task.id, index])), [tasks])
  const start = toDate(timeline.start)
  const dayDate = (offset: number) => new Date(start.getFullYear(), start.getMonth(), start.getDate() + offset)
  const todayOffset = Math.round((toDate(timeline.today).getTime() - start.getTime()) / 86_400_000)

  // Header groups (labels only).
  const months: { label: string; offset: number; length: number }[] = []
  const ticks: { label: string; offset: number; weekend: boolean }[] = []
  for (let offset = 0; offset < timeline.totalDays; offset++) {
    const date = dayDate(offset)
    const monthLabel = date.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' })
    if (!months.length || months[months.length - 1].label !== monthLabel) months.push({ label: monthLabel, offset, length: 1 })
    else months[months.length - 1].length++
    const weekend = date.getDay() === 0 || date.getDay() === 6
    if (scale === 'day') ticks.push({ label: String(date.getDate()), offset, weekend })
    else if (scale === 'week' && date.getDay() === 1) ticks.push({ label: `${date.getDate()}/${date.getMonth() + 1}`, offset, weekend: false })
  }

  const barX = (task: GanttTaskRow, edge: 'start' | 'end') => {
    const bar = task.forecastBar
    if (!bar) return null
    return (edge === 'start' ? bar.offsetDays : bar.offsetDays + bar.durationDays) * pxPerDay
  }

  return <section className="gantt-chart" aria-label="Diagramme de Gantt">
    <div className="gantt-task-list" style={{ paddingTop: HEADER_HEIGHT }}>
      {tasks.map(task => <button key={task.id} type="button" className={`gantt-task-label ${task.id === selectedTaskId ? 'selected' : ''}`} style={{ height: ROW_HEIGHT }} onClick={() => onSelect(task.id)}>
        <strong>{task.critical && <span className="gantt-critical-dot" title="Chemin critique" />}{task.name}</strong>
        <small><span className={`gantt-state gantt-state-${task.scheduleState.toLowerCase()}`}>{stateLabels[task.scheduleState]}</span> {task.progress} %</small>
      </button>)}
    </div>
    <div className="gantt-timeline-scroll">
      <div className="gantt-timeline" style={{ width, height: height + HEADER_HEIGHT }}>
        <div className="gantt-header" style={{ height: HEADER_HEIGHT }}>
          <div className="gantt-months">{months.map(month => <span key={`${month.label}-${month.offset}`} style={{ left: month.offset * pxPerDay, width: month.length * pxPerDay }}>{month.length * pxPerDay > 60 ? month.label : ''}</span>)}</div>
          <div className="gantt-ticks">{ticks.map(tick => <span key={tick.offset} className={tick.weekend ? 'weekend' : ''} style={{ left: tick.offset * pxPerDay, width: scale === 'day' ? pxPerDay : 7 * pxPerDay }}>{tick.label}</span>)}</div>
        </div>
        <div className="gantt-body" style={{ top: HEADER_HEIGHT, height }}>
          {scale === 'day' && ticks.filter(tick => tick.weekend).map(tick => <div key={tick.offset} className="gantt-weekend" style={{ left: tick.offset * pxPerDay, width: pxPerDay }} />)}
          {tasks.map((task, index) => <div key={task.id} className={`gantt-row ${task.id === selectedTaskId ? 'selected' : ''}`} style={{ top: index * ROW_HEIGHT, height: ROW_HEIGHT }} onClick={() => onSelect(task.id)}>
            {task.plannedBar && <div className="gantt-bar-planned" title={`Planifié : ${fmt(task.plannedStart)} → ${fmt(task.plannedEnd)}`} style={{ left: task.plannedBar.offsetDays * pxPerDay, width: Math.max(2, task.plannedBar.durationDays * pxPerDay) }} />}
            {task.forecastBar && <div className={`gantt-bar-forecast ${task.critical ? 'critical' : ''} gantt-bar-${task.scheduleState.toLowerCase()}`}
              title={`${task.name}\nPrévision : ${fmt(task.forecastStart)} → ${fmt(task.forecastEnd)}\nMarge totale : ${days(task.totalFloatDays)} · marge libre : ${days(task.freeFloatDays)}\nAvancement : ${task.progress} % (attendu ${task.expectedProgress} %)`}
              style={{ left: task.forecastBar.offsetDays * pxPerDay, width: Math.max(3, task.forecastBar.durationDays * pxPerDay) }}>
              <span className="gantt-bar-progress" style={{ width: `${task.progress}%` }} />
              {task.forecastBar.durationDays * pxPerDay > 70 && <em>{task.progress} %</em>}
            </div>}
            {task.forecastBar && !!task.totalFloatDays && task.totalFloatDays > 0 && <div className="gantt-bar-float" title={`Marge totale : ${task.totalFloatDays} j`}
              style={{ left: (task.forecastBar.offsetDays + task.forecastBar.durationDays) * pxPerDay, width: Math.min(task.totalFloatDays, timeline.totalDays - task.forecastBar.offsetDays - task.forecastBar.durationDays) * pxPerDay }} />}
            {task.actualBar && <div className="gantt-bar-actual" title={`Réel : ${fmt(task.actualStart)} → ${task.actualEnd ? fmt(task.actualEnd) : 'en cours'}`} style={{ left: task.actualBar.offsetDays * pxPerDay, width: Math.max(2, task.actualBar.durationDays * pxPerDay) }} />}
            {!task.scheduled && <span className="gantt-unscheduled">Non planifiée</span>}
          </div>)}
          <svg className="gantt-links" width={width} height={height} aria-hidden>
            <defs>{['normal', 'critical', 'violated'].map(kind => <marker key={kind} id={`gantt-arrow-${kind}`} viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path d="M0,0 L8,4 L0,8 z" className={`gantt-arrow-${kind}`} /></marker>)}</defs>
            {dependencies.map(link => {
              const from = tasks[rowIndex.get(link.predecessorTaskId) ?? -1]
              const to = tasks[rowIndex.get(link.successorTaskId) ?? -1]
              if (!from || !to) return null
              const fromEdge = link.type === 'START_TO_START' || link.type === 'START_TO_FINISH' ? 'start' : 'end'
              const toEdge = link.type === 'FINISH_TO_FINISH' || link.type === 'START_TO_FINISH' ? 'end' : 'start'
              const x1 = barX(from, fromEdge), x2 = barX(to, toEdge)
              if (x1 === null || x2 === null) return null
              const y1 = (rowIndex.get(from.id)! + 0.5) * ROW_HEIGHT, y2 = (rowIndex.get(to.id)! + 0.5) * ROW_HEIGHT
              const out = fromEdge === 'end' ? x1 + 8 : x1 - 8
              const into = toEdge === 'start' ? x2 - 8 : x2 + 8
              const midY = y1 + (y2 > y1 ? ROW_HEIGHT / 2 : -ROW_HEIGHT / 2)
              const d = `M${x1},${y1} H${out} V${midY} H${into} V${y2} H${x2}`
              const kind = link.violated ? 'violated' : link.critical ? 'critical' : 'normal'
              return <path key={link.id} d={d} className={`gantt-link gantt-link-${kind}`} markerEnd={`url(#gantt-arrow-${kind})`}><title>{`${from.name} → ${to.name} · ${dependencyLabels[link.type]} · décalage ${link.lag} j${link.violation ? `\n⚠ ${link.violation}` : ''}`}</title></path>
            })}
          </svg>
          {todayOffset >= 0 && todayOffset < timeline.totalDays && <div className="gantt-today" style={{ left: (todayOffset + 0.5) * pxPerDay }} title={`Aujourd’hui : ${fmt(timeline.today)}`}><span>Aujourd’hui</span></div>}
        </div>
      </div>
    </div>
  </section>
}

function Legend() {
  return <div className="gantt-legend" aria-label="Légende">
    <span><i className="legend-planned" />Planifié</span>
    <span><i className="legend-forecast" />Prévision (avancement en foncé)</span>
    <span><i className="legend-critical" />Chemin critique</span>
    <span><i className="legend-actual" />Réel</span>
    <span><i className="legend-float" />Marge totale</span>
    <span><i className="legend-violated" />Dépendance non respectée</span>
    <span><i className="legend-today" />Aujourd’hui</span>
  </div>
}

function TaskDetails({ task, gantt, canEdit, saving, onSubmit, onClose }: {
  task: GanttTaskRow; gantt: Gantt; canEdit: boolean; saving: boolean
  onSubmit: (draft: ScheduleDraft) => void; onClose: () => void
}) {
  const [draft, onDraftChange] = useState<ScheduleDraft>(() => ({
    baselineStart: toInput(task.plannedStart), baselineEnd: toInput(task.plannedEnd),
    startDate: toInput(task.actualStart), endDate: toInput(task.actualEnd), progress: task.progress,
  }))
  const predecessors = gantt.dependencies.filter(link => link.successorTaskId === task.id)
  const successors = gantt.dependencies.filter(link => link.predecessorTaskId === task.id)
  const name = (id: string) => gantt.tasks.find(item => item.id === id)?.name ?? '—'
  const today = gantt.timeline.today
  return <section className="form-panel gantt-details" aria-label={`Détails de ${task.name}`}>
    <div className="section-heading"><div><h2>{task.name}</h2><p>{statusLabels[task.status]} · <span className={`gantt-state gantt-state-${task.scheduleState.toLowerCase()}`}>{stateLabels[task.scheduleState]}</span>{task.critical ? ' · sur le chemin critique' : ''}</p></div><button type="button" className="panel-close-button" onClick={onClose} aria-label="Fermer">×</button></div>
    <div className="gantt-details-grid">
      <dl className="metadata-list">
        <div><dt>Planifié</dt><dd>{fmt(task.plannedStart)} → {fmt(task.plannedEnd)} ({days(task.plannedDurationDays)})</dd></div>
        <div><dt>Réel</dt><dd>{fmt(task.actualStart)} → {task.actualStart && !task.actualEnd ? 'en cours' : fmt(task.actualEnd)}</dd></div>
        <div><dt>Prévision</dt><dd>{fmt(task.forecastStart)} → {fmt(task.forecastEnd)}</dd></div>
        <div><dt>Dates au plus tard</dt><dd>{fmt(task.lateStart)} → {fmt(task.lateFinish)}</dd></div>
        <div><dt>Marge totale / libre</dt><dd>{days(task.totalFloatDays)} / {days(task.freeFloatDays)}</dd></div>
        <div><dt>Écart début / fin</dt><dd><span className={varianceClass(task.startVarianceDays)}>{days(task.startVarianceDays, true)}</span> / <span className={varianceClass(task.finishVarianceDays)}>{days(task.finishVarianceDays, true)}</span></dd></div>
        <div><dt>Avancement</dt><dd>{task.progress} % (attendu {task.expectedProgress} %)</dd></div>
        <div><dt>Prédécesseurs</dt><dd>{predecessors.length ? predecessors.map(link => `${name(link.predecessorTaskId)} (${dependencyShort[link.type]}${link.lag ? ` ${link.lag > 0 ? '+' : ''}${link.lag} j` : ''})`).join(', ') : '—'}</dd></div>
        <div><dt>Successeurs</dt><dd>{successors.length ? successors.map(link => `${name(link.successorTaskId)} (${dependencyShort[link.type]}${link.lag ? ` ${link.lag > 0 ? '+' : ''}${link.lag} j` : ''})`).join(', ') : '—'}</dd></div>
      </dl>
      <div>
        {task.warnings.length > 0 && <ul className="gantt-warnings">{task.warnings.map(warning => <li key={warning}>⚠ {warning}</li>)}</ul>}
        {canEdit && <form className="organization-form gantt-schedule-form" onSubmit={event => { event.preventDefault(); onSubmit(draft) }}>
          <h3>Modifier la planification</h3>
          <div className="form-grid">
            <div className="form-field"><label htmlFor="gs-ps">Début planifié</label><input id="gs-ps" type="date" min={gantt.project.plannedStart ?? undefined} max={gantt.project.plannedEnd ?? undefined} value={draft.baselineStart} onChange={event => onDraftChange({ ...draft, baselineStart: event.target.value })} /></div>
            <div className="form-field"><label htmlFor="gs-pe">Fin planifiée</label><input id="gs-pe" type="date" min={draft.baselineStart || gantt.project.plannedStart || undefined} max={gantt.project.plannedEnd ?? undefined} value={draft.baselineEnd} onChange={event => onDraftChange({ ...draft, baselineEnd: event.target.value })} /></div>
            {task.status !== 'TODO' && <div className="form-field"><label htmlFor="gs-as">Début réel</label><input id="gs-as" type="date" max={today} value={draft.startDate} onChange={event => onDraftChange({ ...draft, startDate: event.target.value })} /></div>}
            {task.status === 'DONE' && <div className="form-field"><label htmlFor="gs-ae">Fin réelle</label><input id="gs-ae" type="date" min={draft.startDate || undefined} max={today} value={draft.endDate} onChange={event => onDraftChange({ ...draft, endDate: event.target.value })} /></div>}
            {(task.status === 'STARTED' || task.status === 'BLOCKED') && <div className="form-field full-width"><label htmlFor="gs-pr">Avancement : {draft.progress} %</label><input id="gs-pr" type="range" min={0} max={99} step={5} value={draft.progress} onChange={event => onDraftChange({ ...draft, progress: Number(event.target.value) })} /></div>}
          </div>
          <p className="field-hint">Le statut se modifie depuis la section Tâches. Les dates réelles ne peuvent pas être dans le futur.</p>
          <div className="form-actions"><button className="primary-button" disabled={saving}>{saving ? 'Enregistrement…' : 'Enregistrer'}</button></div>
        </form>}
      </div>
    </div>
  </section>
}
