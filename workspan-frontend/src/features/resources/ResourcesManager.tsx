import { useEffect, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '../../api/http'
import { resourceService } from '../../api/resourceService'
import { spaceService } from '../../api/spaceService'
import type { Resource, Space } from '../../api/types'
import type { EffectiveAccess } from '../../api/types'
import { canSpace } from '../../api/accessPermissions'

type Kind = 'EQUIPMENT' | 'BUDGET'
type ResourceFilter = 'ALL' | 'HUMAN' | 'EQUIPMENT' | 'BUDGET'

export function ResourcesManager({ access }: { access: EffectiveAccess | null }) {
  const [spaces, setSpaces] = useState<Space[]>([])
  const [spaceId, setSpaceId] = useState('')
  const [resources, setResources] = useState<Resource[]>([])
  const [kind, setKind] = useState<Kind>('EQUIPMENT')
  const [filter, setFilter] = useState<ResourceFilter>('ALL')
  const [name, setName] = useState('')
  const [quantity, setQuantity] = useState('1')
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState('USD')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const filteredResources = filter === 'ALL' ? resources : resources.filter(resource => resource.type === filter)
  const people = resources.filter(resource => resource.type === 'HUMAN')
  const equipment = resources.filter(resource => resource.type === 'EQUIPMENT')
  const budgets = resources.filter(resource => resource.type === 'BUDGET')
  const equipmentTotal = equipment.reduce((sum, resource) => sum + (resource.quantity ?? 0), 0)
  const equipmentFree = equipment.reduce((sum, resource) => sum + (resource.availableQuantity ?? 0), 0)
  const budgetCurrencies = [...new Set(budgets.map(resource => resource.currency ?? ''))]

  useEffect(() => {
    const controller = new AbortController()
    spaceService.list({ signal: controller.signal }).then(data => {
      setSpaces(data)
      setSpaceId(current => current || data[0]?.id || '')
    }).catch(cause => {
      if (!controller.signal.aborted) setError(getApiErrorMessage(cause, 'Impossible de charger les espaces.'))
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!spaceId) { setResources([]); return }
    let active = true
    setLoading(true)
    resourceService.listForSpace(spaceId).then(data => { if (active) setResources(data) })
      .catch(cause => { if (active) setError(getApiErrorMessage(cause, 'Impossible de charger les ressources de cet espace.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [spaceId])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setError('')
    try {
      const payload = kind === 'EQUIPMENT'
        ? { type: kind, name: name.trim(), quantity: Number(quantity) }
        : { type: kind, name: name.trim(), amount: Number(amount), currency: currency.toUpperCase() }
      const saved = await resourceService.create(spaceId, payload)
      setResources(current => [...current, saved].sort((a, b) => a.type.localeCompare(b.type) || a.name.localeCompare(b.name)))
      setName(''); setQuantity('1'); setAmount('')
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’ajouter cette ressource.')) }
    finally { setSaving(false) }
  }

  async function remove(resource: Resource) {
    if (!window.confirm(`Supprimer « ${resource.name} » de ce pool ?`)) return
    setSaving(true); setError('')
    try {
      await resourceService.remove(spaceId, resource.id)
      setResources(current => current.filter(item => item.id !== resource.id))
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de supprimer cette ressource.')) }
    finally { setSaving(false) }
  }

  return <div className="crud-layout">
    {canSpace(access, spaceId, 'resource.pool.manage') && <section className="form-panel crud-form-panel">
      <div className="form-panel-heading"><div className="form-icon">R</div><div><h2>Pool de l’espace</h2><p>Les projets de cet espace pourront utiliser ces ressources.</p></div></div>
      <form className="organization-form" onSubmit={submit}><div className="form-grid">
        <div className="form-field full-width"><label htmlFor="resource-space">Espace <span>*</span></label><select id="resource-space" required value={spaceId} onChange={event => { setSpaceId(event.target.value); setError('') }}><option value="">Choisir un espace</option>{spaces.map(space => <option key={space.id} value={space.id}>{space.name}</option>)}</select></div>
        <div className="form-field full-width"><label htmlFor="resource-type">Type <span>*</span></label><select id="resource-type" value={kind} onChange={event => setKind(event.target.value as Kind)}><option value="EQUIPMENT">Équipement</option><option value="BUDGET">Budget</option></select></div>
        <div className="form-field full-width"><label htmlFor="resource-name">Nom <span>*</span></label><input id="resource-name" required maxLength={120} value={name} onChange={event => setName(event.target.value)} /></div>
        {kind === 'EQUIPMENT' ? <div className="form-field full-width"><label htmlFor="resource-quantity">Unités disponibles <span>*</span></label><input id="resource-quantity" type="number" min="1" step="1" required value={quantity} onChange={event => setQuantity(event.target.value)} /></div> : <><div className="form-field"><label htmlFor="resource-amount">Montant disponible <span>*</span></label><input id="resource-amount" type="number" min="0.01" step="0.01" required value={amount} onChange={event => setAmount(event.target.value)} /></div><div className="form-field"><label htmlFor="resource-currency">Devise</label><input id="resource-currency" required maxLength={3} minLength={3} pattern="[A-Za-z]{3}" value={currency} onChange={event => setCurrency(event.target.value)} /></div></>}
      </div>{error && <div className="alert form-alert" role="alert">{error}</div>}
        <div className="form-actions"><button className="primary-button" disabled={saving || !spaceId}>{saving ? 'Enregistrement…' : 'Ajouter au pool'}</button></div>
      </form>
    </section>}
    <section className="crud-list-panel" aria-live="polite">
      <div className="resource-availability-summary">
        <article><span>Employés</span><strong>{people.length} au pool</strong><small>{people.filter(resource => resource.availableQuantity === 0).length} affecté(s) à un projet · {people.filter(resource => resource.availableQuantity !== 0).length} libre(s)</small></article>
        <article><span>Équipements</span><strong>{equipmentFree} / {equipmentTotal} unité(s) libres</strong><small>{equipmentTotal - equipmentFree} unité(s) affectée(s) à des projets</small></article>
        <article><span>Budgets libres</span><strong>{budgetCurrencies.length ? budgetCurrencies.map(currency => `${budgets.filter(resource => (resource.currency ?? '') === currency).reduce((sum, resource) => sum + (resource.availableAmount ?? 0), 0).toLocaleString('fr-FR')} ${currency}`).join(' · ') : 'Aucun budget'}</strong><small>Le solde non réservé reste disponible pour les projets.</small></article>
      </div>
      <div className="section-heading resource-list-heading"><div><h2>Ressources de l’espace</h2><p>Les employés sont gérés dans la section Employés.</p></div><span className="count-pill">{filteredResources.length} affichée(s)</span></div>
      <div className="resource-filter-bar" aria-label="Filtrer par type de ressource">{([['ALL', 'Toutes'], ['HUMAN', 'Employés'], ['EQUIPMENT', 'Équipements'], ['BUDGET', 'Budgets']] as const).map(([value, label]) => <button type="button" key={value} className={filter === value ? 'active' : ''} aria-pressed={filter === value} onClick={() => setFilter(value)}>{label}<span>{value === 'ALL' ? resources.length : resources.filter(resource => resource.type === value).length}</span></button>)}</div>
      {loading ? <div className="empty-state">Chargement du pool…</div> : filteredResources.length ? filteredResources.map(resource => <article className="crud-row" key={resource.id}><div className={`crud-symbol ${resource.type === 'HUMAN' ? 'green' : resource.type === 'BUDGET' ? 'orange' : 'blue'}`}>{resource.type === 'HUMAN' ? 'H' : resource.type === 'BUDGET' ? '$' : 'E'}</div><div className="crud-row-main"><strong>{resource.name}</strong><small>{resource.type === 'HUMAN' ? `${[resource.jobTitle, resource.email].filter(Boolean).join(' · ') || 'Employé'} · ${resource.availableQuantity === 0 ? 'Affecté à un projet' : 'Libre pour un projet'}` : resource.type === 'EQUIPMENT' ? `${resource.availableQuantity ?? 0} libre(s) / ${resource.quantity} unité(s)` : `${resource.availableAmount?.toLocaleString('fr-FR')} ${resource.currency} libres / ${resource.remaining?.toLocaleString('fr-FR')} ${resource.currency} disponibles`}</small></div>{resource.type !== 'HUMAN' && canSpace(access, spaceId, 'resource.pool.manage') && <div className="crud-actions"><button className="danger-button" disabled={saving} onClick={() => void remove(resource)}>Supprimer</button></div>}</article>) : <div className="empty-state"><strong>{resources.length ? 'Aucune ressource de ce type' : 'Pool vide'}</strong><p>{resources.length ? 'Choisissez un autre filtre pour afficher les ressources.' : 'Ajoutez des équipements ou des budgets à cet espace.'}</p></div>}
    </section>
  </div>
}
