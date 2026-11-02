import { useEffect, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '../../api/http'
import { taskResourceService } from '../../api/taskResourceService'
import type { Project, Task, TaskResourceAllocation, TaskResourceOption } from '../../api/types'

function dateLabel(value: string | null) { return value ? new Date(`${value}T00:00:00`).toLocaleDateString('fr-FR') : 'Fin indéterminée' }

export function TaskResourcesPanel({ task, project, onClose }: { task: Task; project: Project; onClose: () => void }) {
  const [pool, setPool] = useState<TaskResourceOption[]>([])
  const [assigned, setAssigned] = useState<TaskResourceAllocation[]>([])
  const [resourceId, setResourceId] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [amount, setAmount] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const selected = pool.find(item => item.resourceId === resourceId)

  async function refresh() {
    const [poolData, assignedData] = await Promise.all([taskResourceService.pool(task.id), taskResourceService.assigned(task.id)])
    setPool(poolData); setAssigned(assignedData)
    setResourceId(current => poolData.some(item => item.resourceId === current && !item.assignedToTask) ? current : poolData.find(item => !item.assignedToTask && (item.type === 'HUMAN' ? (item.availableQuantity ?? 0) > 0 : item.type === 'EQUIPMENT' ? (item.availableQuantity ?? 0) > 0 : (item.availableAmount ?? 0) > 0))?.resourceId ?? '')
  }

  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    Promise.all([taskResourceService.pool(task.id), taskResourceService.assigned(task.id)]).then(([poolData, assignedData]) => {
      if (!active) return
      setPool(poolData); setAssigned(assignedData)
      setResourceId(poolData.find(item => !item.assignedToTask && (item.type === 'HUMAN' ? (item.availableQuantity ?? 0) > 0 : item.type === 'EQUIPMENT' ? (item.availableQuantity ?? 0) > 0 : (item.availableAmount ?? 0) > 0))?.resourceId ?? '')
    }).catch(cause => { if (active) setError(getApiErrorMessage(cause, 'Impossible de charger le pool de ressources de la tâche.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [task.id])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selected) return
    setSaving(true); setError('')
    try {
      const payload = selected.type === 'EQUIPMENT' ? { resourceId, quantity: Number(quantity) }
        : selected.type === 'BUDGET' ? { resourceId, amount: Number(amount) } : { resourceId }
      await taskResourceService.assign(task.id, payload)
      setQuantity('1'); setAmount(''); await refresh()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’affecter cette ressource à la tâche.')) }
    finally { setSaving(false) }
  }

  async function remove(item: TaskResourceAllocation) {
    setSaving(true); setError('')
    try { await taskResourceService.remove(task.id, item.resourceId); await refresh() }
    catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de libérer cette ressource.')) }
    finally { setSaving(false) }
  }

  const plannedStart = task.startDate ?? project.startDate
  const plannedEnd = task.endDate ?? project.endDate
  const today = new Date().toISOString().slice(0, 10)

  return <section className="task-resource-panel" aria-label={`Ressources de la tâche ${task.name}`}>
    <div className="section-heading"><div><h2>Ressources · {task.name}</h2><p>Les ressources sont prises dans le pool du projet. La disponibilité suit les dates de la tâche et du projet.</p></div><button type="button" className="panel-close-button" onClick={onClose} aria-label="Fermer">×</button></div>
    {error && <div className="alert crud-alert" role="alert">{error}</div>}
    {loading ? <div className="empty-state">Chargement du pool…</div> : <div className="task-resource-content">
      <div className="task-resource-window"><strong>Période prévue</strong><span>{dateLabel(plannedStart)} – {dateLabel(plannedEnd)}</span><small>La tâche utilise les dates du projet lorsqu’elle n’a pas de date propre.</small></div>
      <form className="project-resource-form" onSubmit={submit}>
        <p className="field-hint">Le budget affecte reste engage apres la fin de la tache. Retirez-le avant la fin pour le rendre au pool du projet.</p>
        <div className="form-field"><label htmlFor={`task-pool-resource-${task.id}`}>Ressource disponible <span>*</span></label><select id={`task-pool-resource-${task.id}`} required value={resourceId} onChange={event => { setResourceId(event.target.value); setAmount(''); setQuantity('1') }}><option value="">Choisir dans le pool du projet</option>{pool.filter(item => !item.assignedToTask && (item.type === 'HUMAN' ? (item.availableQuantity ?? 0) > 0 : item.type === 'EQUIPMENT' ? (item.availableQuantity ?? 0) > 0 : (item.availableAmount ?? 0) > 0)).map(item => <option key={item.resourceId} value={item.resourceId}>{item.type === 'HUMAN' ? 'Employé' : item.type === 'EQUIPMENT' ? 'Équipement' : 'Budget'} · {item.name}</option>)}</select></div>
        {selected?.type === 'EQUIPMENT' && <div className="form-field"><label htmlFor={`task-pool-quantity-${task.id}`}>Unités disponibles sur cette période ({selected.availableQuantity})</label><input id={`task-pool-quantity-${task.id}`} type="number" min="1" max={selected.availableQuantity ?? undefined} step="1" required value={quantity} onChange={event => setQuantity(event.target.value)} /></div>}
        {selected?.type === 'BUDGET' && <div className="form-field"><label htmlFor={`task-pool-amount-${task.id}`}>Budget disponible sur cette période ({selected.availableAmount} {selected.currency})</label><input id={`task-pool-amount-${task.id}`} type="number" min="0.01" max={selected.availableAmount ?? undefined} step="0.01" required value={amount} onChange={event => setAmount(event.target.value)} /></div>}
        <button type="submit" className="primary-button" disabled={saving || task.status === 'DONE' || !resourceId}>{saving ? 'Enregistrement…' : 'Affecter à la tâche'}</button>
        {task.status === 'DONE' && <small className="field-hint">Cette tâche est terminée : ses ressources sont libérées.</small>}
      </form>
      <div className="task-resource-assigned"><h3>Ressources de cette tâche ({assigned.length})</h3>{assigned.length ? assigned.map(item => { const state = item.active ? item.endDate ? `Affectée jusqu’au ${dateLabel(item.endDate)} · libre le ${dateLabel(item.availableFrom)}` : 'Affectée · fin indéterminée' : task.status === 'DONE' ? 'Libérée, tâche terminée' : item.startDate && today < item.startDate ? 'Réservation prévue' : `Libérée${item.availableFrom ? ` le ${dateLabel(item.availableFrom)}` : ''}`; return <article className="crud-row" key={item.resourceId}><div className={`crud-symbol ${item.type === 'HUMAN' ? 'green' : item.type === 'BUDGET' ? 'orange' : 'blue'}`}>{item.type === 'HUMAN' ? 'H' : item.type === 'BUDGET' ? '$' : 'E'}</div><div className="crud-row-main"><strong>{item.name}</strong><small>{item.type === 'HUMAN' ? '1 employé' : item.type === 'EQUIPMENT' ? `${item.quantity} unité(s)` : `${item.amount} ${item.currency}`} · {state} · {dateLabel(item.startDate)} – {dateLabel(item.endDate)}</small><small>Ajoutée le {new Date(item.allocatedAt).toLocaleDateString('fr-FR')}</small></div><button type="button" className="secondary-button" disabled={saving} onClick={() => void remove(item)}>Retirer</button></article>}) : <p className="field-hint">Aucune ressource n’est affectée à cette tâche.</p>}</div>
    </div>}
  </section>
}
