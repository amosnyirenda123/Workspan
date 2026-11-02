import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '../../api/http'
import { projectResourceService } from '../../api/projectResourceService'
import type { Project, ProjectResourceAllocation, Resource } from '../../api/types'

export function ProjectResourcesPanel({ project, onClose }: { project: Project; onClose: () => void }) {
  const [pool, setPool] = useState<Resource[]>([])
  const [assigned, setAssigned] = useState<ProjectResourceAllocation[]>([])
  const [resourceId, setResourceId] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [amount, setAmount] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const assignedIds = useMemo(() => new Set(assigned.map(item => item.resourceId)), [assigned])
  const canAssign = (resource: Resource) => resource.type === 'HUMAN' || (resource.type === 'EQUIPMENT' ? (resource.availableQuantity ?? 0) > 0 : (resource.availableAmount ?? 0) > 0)
  const selectedResource = pool.find(resource => resource.id === resourceId)
  const humanCount = assigned.filter(item => item.type === 'HUMAN').length
  const humanAssignedNow = assigned.filter(item => item.type === 'HUMAN' && item.active && item.activeTaskCount > 0).length
  const humanScheduled = assigned.filter(item => item.type === 'HUMAN').reduce((sum, item) => sum + item.scheduledTaskCount, 0)
  const equipment = assigned.filter(item => item.type === 'EQUIPMENT')
  const equipmentUnits = equipment.reduce((sum, item) => sum + (item.quantity ?? 0), 0)
  const equipmentTaskUnits = equipment.reduce((sum, item) => sum + (item.active ? item.taskAssignedQuantity ?? 0 : 0), 0)
  const equipmentScheduledUnits = equipment.reduce((sum, item) => sum + (item.active ? item.scheduledTaskQuantity ?? 0 : 0), 0)
  const equipmentFreeUnits = equipment.reduce((sum, item) => sum + (item.active ? item.freeQuantity ?? 0 : item.quantity ?? 0), 0)
  const budgets = assigned.filter(item => item.type === 'BUDGET')
  const today = new Date().toISOString().slice(0, 10)
  const budgetSummary = [...new Set(budgets.map(item => item.currency ?? ''))].map(currency => ({
    currency,
    total: budgets.filter(item => (item.currency ?? '') === currency).reduce((sum, item) => sum + (item.amount ?? 0), 0),
    task: budgets.filter(item => (item.currency ?? '') === currency).reduce((sum, item) => sum + (item.taskAssignedAmount ?? 0), 0),
    scheduled: budgets.filter(item => (item.currency ?? '') === currency && item.active).reduce((sum, item) => sum + (item.scheduledTaskAmount ?? 0), 0),
    free: budgets.filter(item => (item.currency ?? '') === currency).reduce((sum, item) => sum + (item.freeAmount ?? 0), 0),
  }))
  const projectAllocationState = (item: ProjectResourceAllocation) => item.active
    ? item.endDate ? `Affectée jusqu’au ${new Date(`${item.endDate}T00:00:00`).toLocaleDateString('fr-FR')} · libre le ${item.availableFrom ? new Date(`${item.availableFrom}T00:00:00`).toLocaleDateString('fr-FR') : 'à la fin du projet'}` : 'Affectée au projet · fin indéterminée'
    : item.startDate && today < item.startDate ? `Libre jusqu’au ${new Date(`${item.startDate}T00:00:00`).toLocaleDateString('fr-FR')}` : `Libérée${item.availableFrom ? ` depuis le ${new Date(`${item.availableFrom}T00:00:00`).toLocaleDateString('fr-FR')}` : ''}`

  async function refresh() {
    const [poolData, assignedData] = await Promise.all([projectResourceService.pool(project.id), projectResourceService.assigned(project.id)])
    setPool(poolData); setAssigned(assignedData)
    setResourceId(current => poolData.some(resource => resource.id === current && !assignedData.some(item => item.resourceId === current))
      ? current : poolData.find(resource => !assignedData.some(item => item.resourceId === resource.id))?.id ?? '')
  }

  useEffect(() => {
    let active = true
    setLoading(true); setError('')
    Promise.all([projectResourceService.pool(project.id), projectResourceService.assigned(project.id)])
      .then(([poolData, assignedData]) => {
        if (!active) return
        setPool(poolData); setAssigned(assignedData)
        setResourceId(poolData.find(resource => !assignedData.some(item => item.resourceId === resource.id))?.id ?? '')
      }).catch(cause => { if (active) setError(getApiErrorMessage(cause, 'Impossible de charger le pool du projet.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [project.id])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!selectedResource) return
    setSaving(true); setError('')
    try {
      const payload = selectedResource.type === 'EQUIPMENT'
        ? { resourceId, quantity: Number(quantity) }
        : selectedResource.type === 'BUDGET' ? { resourceId, amount: Number(amount) } : { resourceId }
      await projectResourceService.assign(project.id, payload)
      setAmount(''); setQuantity('1'); await refresh()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’ajouter cette ressource au projet.')) }
    finally { setSaving(false) }
  }

  async function remove(resource: ProjectResourceAllocation) {
    setSaving(true); setError('')
    try { await projectResourceService.remove(project.id, resource.resourceId); await refresh() }
    catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de libérer cette ressource.')) }
    finally { setSaving(false) }
  }

  return <section id={`project-resource-panel-${project.id}`} className="project-resource-panel">
    <div className="section-heading"><div><h2>Pool de ressources · {project.name}</h2><p>Les ressources proposées viennent de l’espace du projet.</p></div><button className="panel-close-button" type="button" onClick={onClose} aria-label="Fermer">×</button></div>
    {error && <div className="alert crud-alert" role="alert">{error}</div>}
    {loading ? <div className="empty-state">Chargement du pool…</div> : <div className="project-resource-content">
      <div className="project-resource-stats">
        <article><span>Employés affectés au projet</span><strong>{humanCount}</strong><small>{humanScheduled} affecté(s) à des tâches ({humanAssignedNow} actif(s)) · {humanCount - humanAssignedNow} libre(s) maintenant</small></article>
        <article><span>Équipements affectés au projet</span><strong>{equipment.length} ressource(s) · {equipmentUnits} unité(s)</strong><small>{equipmentScheduledUnits} réservée(s) pour des tâches ({equipmentTaskUnits} active(s)) · {equipmentFreeUnits} libre(s) maintenant</small></article>
        <article><span>Budgets affectés au projet</span><strong>{budgets.length} ressource(s)</strong><small>{budgetSummary.length ? budgetSummary.map(item => `${item.total.toLocaleString('fr-FR')} ${item.currency} au projet · ${item.scheduled.toLocaleString('fr-FR')} réservé(s) aux tâches (${item.task.toLocaleString('fr-FR')} actif(s)) · ${item.free.toLocaleString('fr-FR')} libre(s)`).join(' / ') : 'Aucun budget affecté'}</small></article>
      </div>
      <form className="project-resource-form" onSubmit={submit}>
        <div className="form-field"><label htmlFor={`pool-resource-${project.id}`}>Ressource à ajouter <span>*</span></label><select id={`pool-resource-${project.id}`} required value={resourceId} onChange={event => { setResourceId(event.target.value); setAmount(''); setQuantity('1') }}><option value="">Choisir dans le pool de l’espace</option>{pool.filter(resource => !assignedIds.has(resource.id) && canAssign(resource)).map(resource => <option key={resource.id} value={resource.id}>{resource.type === 'HUMAN' ? 'Employé' : resource.type === 'EQUIPMENT' ? 'Équipement' : 'Budget'} · {resource.name}</option>)}</select></div>
        {selectedResource?.type === 'EQUIPMENT' && <div className="form-field"><label htmlFor={`pool-quantity-${project.id}`}>Unités à réserver ({selectedResource.availableQuantity} disponibles) <span>*</span></label><input id={`pool-quantity-${project.id}`} type="number" min="1" max={selectedResource.availableQuantity ?? undefined} step="1" required value={quantity} onChange={event => setQuantity(event.target.value)} /></div>}
        {selectedResource?.type === 'BUDGET' && <div className="form-field"><label htmlFor={`pool-amount-${project.id}`}>Montant à réserver ({selectedResource.availableAmount} {selectedResource.currency} disponibles) <span>*</span></label><input id={`pool-amount-${project.id}`} type="number" min="0.01" max={selectedResource.availableAmount ?? undefined} step="0.01" required value={amount} onChange={event => setAmount(event.target.value)} /></div>}
        {selectedResource?.type === 'HUMAN' && <p className="field-hint">Un employé est ajouté individuellement au projet.</p>}
        <button className="primary-button" disabled={saving || !resourceId || pool.every(resource => assignedIds.has(resource.id) || !canAssign(resource))}>{saving ? 'Enregistrement…' : 'Ajouter au projet'}</button>
      </form>
      <div className="project-assignment-list"><h3>Ressources affectées au projet ({assigned.length})</h3>{assigned.length ? assigned.map(item => <article className="crud-row" key={item.resourceId}><div className={`crud-symbol ${item.type === 'HUMAN' ? 'green' : item.type === 'BUDGET' ? 'orange' : 'blue'}`}>{item.type === 'HUMAN' ? 'H' : item.type === 'BUDGET' ? '$' : 'E'}</div><div className="crud-row-main"><strong>{item.name}</strong><small>{item.type === 'HUMAN' ? `1 employé au projet · ${item.scheduledTaskCount} tâche(s) prévue(s) · ${item.freeQuantity ?? 0} libre(s) maintenant` : item.type === 'EQUIPMENT' ? `${item.quantity} unité(s) au projet · ${item.scheduledTaskQuantity ?? 0} réservée(s) aux tâches · ${item.freeQuantity ?? 0} libre(s) maintenant` : `${item.amount} ${item.currency} au projet · ${item.scheduledTaskAmount ?? 0} ${item.currency} réservé(s) aux tâches · ${item.freeAmount ?? 0} ${item.currency} libre(s) maintenant`}</small><small>Ajoutée le {new Date(item.allocatedAt).toLocaleDateString('fr-FR')} · {projectAllocationState(item)}{item.activeTaskCount ? ` · ${item.activeTaskCount} tâche(s) active(s)` : ''}</small></div><div className="crud-actions"><button type="button" className="danger-button" disabled={saving} onClick={() => void remove(item)}>Libérer</button></div></article>) : <p className="field-hint">Aucune ressource affectée. Les ressources disponibles apparaissent selon l’espace du projet.</p>}</div>
    </div>}
  </section>
}
