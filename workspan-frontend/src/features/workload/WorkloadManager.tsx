import { useEffect, useMemo, useState } from 'react'
import { canProject, canSpace } from '../../api/accessPermissions'
import { getApiErrorMessage } from '../../api/http'
import { projectService } from '../../api/projectService'
import { spaceService } from '../../api/spaceService'
import type { EffectiveAccess, LevelingAction, LevelingProposal, Project, Space, TaskStatus, Workload, WorkloadAssignment, WorkloadCandidate, WorkloadResource } from '../../api/types'
import { workloadService, type WorkloadScope } from '../../api/workloadService'
import './workload.css'

/*
 * Display-only workload view. Daily loads, utilisation, overload periods, candidates and leveling proposals all come
 * from /api/workload. The component only maps utilisation values to colours and lays out the grid.
 */

const statusLabels: Record<TaskStatus, string> = { TODO: 'À faire', STARTED: 'En cours', BLOCKED: 'Bloquée', DONE: 'Terminée' }
const toDate = (iso: string) => new Date(`${iso}T00:00:00`)
const fmt = (value: string | null | undefined) => value ? toDate(value).toLocaleDateString('fr-FR') : '—'
const isoDay = (date: Date) => `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
const addDays = (iso: string, days: number) => { const date = toDate(iso); date.setDate(date.getDate() + days); return isoDay(date) }
const band = (utilisation: number) => utilisation <= 0 ? 'empty' : utilisation > 100 ? 'over' : utilisation >= 80 ? 'full' : 'low'
const loadLabel = (resource: WorkloadResource, value: number) => resource.unit === 'PERCENT' ? `${value} %` : `${value}/${resource.capacity} u.`

type ScopeOption = { key: string; label: string; scope: WorkloadScope; spaceId: string }

export function WorkloadManager({ access }: { access: EffectiveAccess | null }) {
  const [spaces, setSpaces] = useState<Space[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [listsReady, setListsReady] = useState(false)
  const [chosenScope, setChosenScope] = useState('')
  const [from, setFrom] = useState(() => addDays(isoDay(new Date()), -7))
  const [weeks, setWeeks] = useState(8)
  const [data, setData] = useState<Workload | null>(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [proposal, setProposal] = useState<LevelingProposal | null>(null)
  const [chosenActions, setChosenActions] = useState<Set<number>>(new Set())
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([spaceService.list({ signal: controller.signal }), projectService.list({ signal: controller.signal })])
      .then(([spaceData, projectData]) => { setSpaces(spaceData); setProjects(projectData); setListsReady(true) })
      .catch(cause => { if (!controller.signal.aborted) { setError(getApiErrorMessage(cause, 'Impossible de charger les espaces et projets.')); setLoading(false) } })
    return () => controller.abort()
  }, [])

  const options = useMemo<ScopeOption[]>(() => [
    ...spaces.filter(space => canSpace(access, space.id, 'workload.view'))
      .map(space => ({ key: `space:${space.id}`, label: `Espace · ${space.name}`, scope: { kind: 'space' as const, id: space.id }, spaceId: space.id })),
    ...projects.filter(project => canProject(access, project.id, project.spaceId, 'workload.view'))
      .map(project => ({ key: `project:${project.id}`, label: `Projet · ${project.name}`, scope: { kind: 'project' as const, id: project.id }, spaceId: project.spaceId })),
  ], [spaces, projects, access])
  const option = options.find(item => item.key === chosenScope) ?? options[0] ?? null
  const scopeKey = option?.key ?? ''
  const to = addDays(from, weeks * 7 - 1)
  const canManage = !!option && (option.scope.kind === 'space'
    ? canSpace(access, option.scope.id, 'workload.manage')
    : canProject(access, option.scope.id, option.spaceId, 'workload.manage'))

  useEffect(() => {
    if (!option) return
    const controller = new AbortController()
    workloadService.analyse(option.scope, from, to, { signal: controller.signal })
      .then(result => { setData(result); setError('') })
      .catch(cause => { if (!controller.signal.aborted) { setData(null); setError(getApiErrorMessage(cause, 'Impossible de calculer la charge.')) } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
    // option is derived from scopeKey; re-run only when the scope or the period changes
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scopeKey, from, to, reloadKey])

  function reload(message = '') {
    setLoading(true); setProposal(null); setNotice(message); setReloadKey(key => key + 1)
  }

  function changeScope(key: string) {
    setLoading(true); setChosenScope(key); setSelectedId(null); setProposal(null); setNotice(''); setError('')
  }

  async function run(action: () => Promise<string>, fallback: string) {
    setSaving(true); setError('')
    try { reload(await action()) }
    catch (cause) { setError(getApiErrorMessage(cause, fallback)) }
    finally { setSaving(false) }
  }

  async function propose() {
    if (!option) return
    setSaving(true); setError(''); setNotice('')
    try {
      const result = await workloadService.leveling(option.scope, from, to)
      setProposal(result); setChosenActions(new Set(result.actions.map((_, index) => index)))
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de calculer une proposition de lissage.')) }
    finally { setSaving(false) }
  }

  function applyProposal() {
    if (!proposal) return
    const selected = proposal.actions.filter((_, index) => chosenActions.has(index))
    if (!selected.length) return
    void run(async () => {
      const result = await workloadService.applyLeveling(selected)
      return `${result.applied} action(s) de lissage appliquée(s).`
    }, 'Impossible d’appliquer la proposition. Recalculez-la puis réessayez.')
  }

  if (listsReady && !options.length && !error) return <div className="empty-state"><strong>Aucun périmètre disponible</strong><p>La permission « Consulter la charge des ressources » ne vous est attribuée sur aucun espace ni projet.</p></div>

  const selected = data?.resources.find(resource => resource.resourceId === selectedId) ?? null

  return <div className="workload-manager">
    <div className="crud-heading workload-heading">
      <div><p className="eyebrow">CHARGE DE TRAVAIL</p><h2>Charge des ressources</h2><p>Charge quotidienne des employés (% de la journée) et des équipements (unités), tous projets confondus. Calculée par le serveur à partir des dates prévues des tâches.</p></div>
      <div className="workload-toolbar">
        <div className="form-field"><label htmlFor="workload-scope">Périmètre</label><select id="workload-scope" value={scopeKey} onChange={event => changeScope(event.target.value)}>{options.map(item => <option key={item.key} value={item.key}>{item.label}</option>)}</select></div>
        <div className="form-field"><label htmlFor="workload-from">Du</label><input id="workload-from" type="date" value={from} onChange={event => { if (event.target.value) { setLoading(true); setFrom(event.target.value) } }} /></div>
        <div className="form-field"><label htmlFor="workload-weeks">Durée</label><select id="workload-weeks" value={weeks} onChange={event => { setLoading(true); setWeeks(Number(event.target.value)) }}>{[2, 4, 8, 12, 26].map(value => <option key={value} value={value}>{value} semaines</option>)}</select></div>
        <button type="button" className="secondary-button" disabled={loading || saving} onClick={() => reload()}>Actualiser</button>
        {canManage && <button type="button" className="primary-button" aria-busy={saving} disabled={loading || saving || !data?.summary.overloadDays} onClick={() => void propose()}>Proposer un lissage</button>}
      </div>
    </div>

    {error && <div className="alert crud-alert" role="alert">{error}</div>}
    {notice && <div className="workload-notice" role="status">{notice}</div>}

    {loading && !data ? <div className="empty-state">Calcul de la charge…</div> : data && <>
      <section className="workload-kpis" aria-label="Synthèse de la charge">
        <article className={data.summary.overloadedResources ? 'alert-kpi' : 'ok-kpi'}><span>Ressources en surcharge</span><strong>{data.summary.overloadedResources}/{data.summary.resourcesAnalysed}</strong><small>{data.summary.overloadDays} jour(s) de surcharge à venir</small></article>
        <article><span>Pic de charge</span><strong>{data.summary.peakUtilisation} %</strong><small>sur la période</small></article>
        <article><span>Charge moyenne</span><strong>{data.summary.averageUtilisation} %</strong><small>{fmt(data.from)} → {fmt(data.to)}</small></article>
        <article><span>Affectations non planifiées</span><strong>{data.summary.unscheduledAssignments}</strong><small>sans dates, non comptées</small></article>
      </section>

      {proposal && <LevelingPanel proposal={proposal} chosen={chosenActions} saving={saving} onToggle={index => setChosenActions(current => { const next = new Set(current); if (next.has(index)) next.delete(index); else next.add(index); return next })}
        onApply={applyProposal} onClose={() => setProposal(null)} />}

      {data.resources.length === 0 ? <div className="empty-state"><strong>Aucune ressource</strong><p>Aucun employé ni équipement dans ce périmètre.</p></div> : <>
        <Heatmap data={data} selectedId={selectedId} onSelect={id => setSelectedId(current => current === id ? null : id)} />
        <div className="workload-legend"><span><i className="cell-low" />Moins de 80 %</span><span><i className="cell-full" />80 à 100 %</span><span><i className="cell-over" />Surcharge (&gt; 100 %)</span><span><i className="cell-today" />Aujourd’hui</span></div>
      </>}

      {selected && <ResourceDetails key={selected.resourceId} resource={selected} canManage={canManage} saving={saving} onClose={() => setSelectedId(null)}
        onReassign={(assignment, candidate) => void run(async () => {
          await workloadService.reassign(assignment.taskId!, selected.resourceId, candidate.resourceId)
          return `« ${assignment.taskName} » est réaffectée à ${candidate.name}.`
        }, 'Réaffectation impossible.')}
        onShift={(assignment, days) => void run(async () => {
          await workloadService.shift(assignment.taskId!, days)
          return `« ${assignment.taskName} » est décalée de ${days} jour(s).`
        }, 'Décalage impossible.')} />}
    </>}
  </div>
}

function Heatmap({ data, selectedId, onSelect }: { data: Workload; selectedId: string | null; onSelect: (id: string) => void }) {
  const cell = data.days <= 31 ? 28 : data.days <= 92 ? 16 : 8
  const showValues = cell >= 28
  const start = toDate(data.from)
  const days = Array.from({ length: data.days }, (_, index) => { const date = new Date(start); date.setDate(start.getDate() + index); return date })
  const todayIndex = Math.round((toDate(data.today).getTime() - start.getTime()) / 86_400_000)
  const months: { label: string; index: number; length: number }[] = []
  days.forEach((date, index) => {
    const label = date.toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' })
    if (!months.length || months[months.length - 1].label !== label) months.push({ label, index, length: 1 })
    else months[months.length - 1].length++
  })
  return <section className="workload-heatmap" aria-label="Charge quotidienne par ressource">
    <div className="workload-names">
      <div className="workload-corner">Ressource</div>
      {data.resources.map(resource => <button type="button" key={resource.resourceId} className={`workload-name ${resource.resourceId === selectedId ? 'selected' : ''}`} onClick={() => onSelect(resource.resourceId)}>
        <strong>{resource.overloadDays > 0 && <span className="workload-dot" title="Surcharge" />}{resource.name}</strong>
        <small>{resource.type === 'HUMAN' ? `Employé${resource.jobTitle ? ` · ${resource.jobTitle}` : ''}` : `Équipement · ${resource.capacity} unité(s)`} · pic {resource.peakUtilisation} %</small>
      </button>)}
    </div>
    <div className="workload-grid-scroll">
      <div className="workload-grid" style={{ width: data.days * cell }}>
        <div className="workload-months">{months.map(month => <span key={month.index} style={{ left: month.index * cell, width: month.length * cell }}>{month.length * cell > 70 ? month.label : ''}</span>)}</div>
        <div className="workload-days">{days.map((date, index) => <span key={index} className={`${date.getDay() % 6 === 0 ? 'weekend' : ''} ${index === todayIndex ? 'today' : ''}`} style={{ width: cell }}>{cell >= 16 ? date.getDate() : ''}</span>)}</div>
        {data.resources.map(resource => <div key={resource.resourceId} className={`workload-row ${resource.resourceId === selectedId ? 'selected' : ''}`} onClick={() => onSelect(resource.resourceId)}>
          {resource.utilisation.map((value, index) => <span key={index} className={`workload-cell cell-${band(value)} ${index === todayIndex ? 'today' : ''}`} style={{ width: cell }}
            title={`${resource.name} · ${days[index].toLocaleDateString('fr-FR')} : ${loadLabel(resource, resource.load[index])}${value > 100 ? ' — surcharge' : ''}`}>{showValues && value > 0 ? value : ''}</span>)}
        </div>)}
      </div>
    </div>
  </section>
}

function ResourceDetails({ resource, canManage, saving, onClose, onReassign, onShift }: {
  resource: WorkloadResource; canManage: boolean; saving: boolean; onClose: () => void
  onReassign: (assignment: WorkloadAssignment, candidate: WorkloadCandidate) => void
  onShift: (assignment: WorkloadAssignment, days: number) => void
}) {
  const [openTaskId, setOpenTaskId] = useState<string | null>(null)
  const [candidates, setCandidates] = useState<WorkloadCandidate[] | null>(null)
  const [candidateError, setCandidateError] = useState('')
  const [shiftDays, setShiftDays] = useState<Record<string, number>>({})

  async function openCandidates(assignment: WorkloadAssignment) {
    if (!assignment.taskId) return
    if (openTaskId === assignment.taskId) { setOpenTaskId(null); return }
    setOpenTaskId(assignment.taskId); setCandidates(null); setCandidateError('')
    try { setCandidates(await workloadService.candidates(assignment.taskId, resource.resourceId)) }
    catch (cause) { setCandidateError(getApiErrorMessage(cause, 'Impossible de charger les employés disponibles.')) }
  }

  const human = resource.type === 'HUMAN'
  return <section className="form-panel workload-details" aria-label={`Charge de ${resource.name}`}>
    <div className="section-heading"><div><h2>{resource.name}</h2><p>{human ? 'Capacité : 100 % par jour' : `Capacité : ${resource.capacity} unité(s)`} · pic {loadLabel(resource, resource.peakLoad)} ({resource.peakUtilisation} %) · moyenne {resource.averageUtilisation} %</p></div><button type="button" className="panel-close-button" onClick={onClose} aria-label="Fermer">×</button></div>
    {resource.overloads.length > 0 ? <ul className="workload-periods">{resource.overloads.map(period => <li key={period.start}>⚠ Surcharge du <strong>{fmt(period.start)}</strong> au <strong>{fmt(period.end)}</strong> ({period.days} j) · pic {loadLabel(resource, period.peakLoad)}</li>)}</ul>
      : <p className="workload-ok">Aucune surcharge sur la période.</p>}
    <div className="workload-table-scroll"><table className="workload-table">
      <thead><tr><th>Tâche</th><th>Projet</th><th>Période prévue</th><th>Charge</th><th>Marge libre / totale</th><th>État</th>{canManage && <th>Redistribuer</th>}</tr></thead>
      <tbody>{resource.assignments.map((assignment, index) => {
        const key = assignment.taskId ?? `hidden-${index}`
        const maxShift = assignment.totalFloatDays ?? 0
        const days = Math.min(Math.max(1, shiftDays[key] ?? 1), Math.max(1, maxShift))
        return [<tr key={key} className={assignment.overloaded ? 'overloaded' : ''}>
          <td><strong>{assignment.critical && <span className="workload-critical" title="Chemin critique">●</span>}{assignment.taskName}</strong><small>{statusLabels[assignment.status]}</small></td>
          <td>{assignment.projectName}</td>
          <td>{assignment.scheduled ? <>{fmt(assignment.start)}<small>→ {fmt(assignment.end)}</small></> : <small>Non planifiée</small>}</td>
          <td>{human ? `${assignment.load} %` : `${assignment.load} u.`}</td>
          <td>{assignment.freeFloatDays ?? '—'} j / {assignment.totalFloatDays ?? '—'} j</td>
          <td>{assignment.overloaded ? <span className="workload-badge over">En surcharge</span> : <span className="workload-badge">OK</span>}{assignment.critical && <span className="workload-badge critical">Critique</span>}</td>
          {canManage && <td className="workload-actions">{assignment.visible && assignment.status !== 'DONE' ? <>
            {human && <button type="button" className="secondary-button" disabled={saving} onClick={() => void openCandidates(assignment)}>{openTaskId === assignment.taskId ? 'Fermer' : 'Réaffecter'}</button>}
            {assignment.shiftable && <span className="workload-shift"><input aria-label="Jours de décalage" type="number" min={1} max={maxShift} value={days} onChange={event => setShiftDays(current => ({ ...current, [key]: Number(event.target.value) }))} /><button type="button" className="secondary-button" disabled={saving} title={`Décaler de 1 à ${maxShift} jour(s) sans retarder la fin du projet`} onClick={() => onShift(assignment, days)}>Décaler</button></span>}
            {!human && !assignment.shiftable && <small>—</small>}
          </> : <small>—</small>}</td>}
        </tr>,
        openTaskId && openTaskId === assignment.taskId && <tr key={`${key}-candidates`} className="workload-candidates-row"><td colSpan={canManage ? 7 : 6}>
          {candidateError ? <div className="alert" role="alert">{candidateError}</div> : !candidates ? <small>Recherche des employés disponibles…</small> : candidates.length === 0 ? <small>Aucun autre employé dans cet espace.</small> :
            <div className="workload-candidates">{candidates.map(candidate => <article key={candidate.resourceId} className={candidate.fits ? 'fits' : 'busy'}>
              <div><strong>{candidate.name}</strong><small>{candidate.jobTitle ?? 'Employé'} · {candidate.inProjectPool ? 'dans le pool du projet' : 'sera ajouté au pool du projet'}</small></div>
              <div className="workload-candidate-load"><strong>{candidate.peakUtilisationWithTask} %</strong><small>pic avec cette tâche</small></div>
              <button type="button" className={candidate.fits ? 'primary-button' : 'secondary-button'} disabled={saving} onClick={() => onReassign(assignment, candidate)}>{candidate.fits ? 'Réaffecter' : 'Réaffecter quand même'}</button>
            </article>)}</div>}
        </td></tr>]
      })}</tbody>
    </table></div>
  </section>
}

function LevelingPanel({ proposal, chosen, saving, onToggle, onApply, onClose }: {
  proposal: LevelingProposal; chosen: Set<number>; saving: boolean; onToggle: (index: number) => void; onApply: () => void; onClose: () => void
}) {
  const describe = (action: LevelingAction) => action.type === 'SHIFT'
    ? `Décaler « ${action.taskName} » de ${action.shiftDays} jour(s) : ${fmt(action.newStart)} → ${fmt(action.newEnd)}`
    : `Réaffecter « ${action.taskName} » de ${action.fromResourceName} à ${action.toResourceName}${action.requiresPoolAddition ? ' (ajout au pool du projet)' : ''}`
  return <section className="form-panel workload-leveling" aria-label="Proposition de lissage">
    <div className="section-heading"><div><h2>Proposition de lissage</h2><p>Jours de surcharge : <strong>{proposal.overloadDaysBefore}</strong> → <strong>{proposal.overloadDaysAfter}</strong>. Les décalages restent dans la marge libre : aucune autre tâche ni la fin du projet ne bougent.</p></div><button type="button" className="panel-close-button" onClick={onClose} aria-label="Fermer">×</button></div>
    {proposal.actions.length ? <ul className="workload-actions-list">{proposal.actions.map((action, index) => <li key={`${action.type}-${action.taskId}-${action.fromResourceId ?? ''}`}>
      <label><input type="checkbox" checked={chosen.has(index)} onChange={() => onToggle(index)} /><span className={`workload-badge ${action.type === 'SHIFT' ? 'shift' : 'reassign'}`}>{action.type === 'SHIFT' ? 'Décalage' : 'Réaffectation'}</span>
        <span><strong>{describe(action)}</strong><small>{action.projectName} · {action.reason}</small></span></label>
    </li>)}</ul> : <p className="workload-ok">Aucune action automatique possible.</p>}
    {proposal.unresolved.length > 0 && <ul className="workload-periods">{proposal.unresolved.map(message => <li key={message}>⚠ {message}</li>)}</ul>}
    <div className="form-actions"><button type="button" className="primary-button" aria-busy={saving} disabled={saving || chosen.size === 0} onClick={onApply}>Appliquer la sélection ({chosen.size})</button><button type="button" className="secondary-button" onClick={onClose}>Fermer</button></div>
  </section>
}
