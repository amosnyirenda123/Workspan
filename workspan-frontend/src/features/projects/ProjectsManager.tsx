import { useEffect, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '../../api/http'
import { projectService } from '../../api/projectService'
import { projectResourceService } from '../../api/projectResourceService'
import { resourceService } from '../../api/resourceService'
import { spaceService } from '../../api/spaceService'
import type { Project, ProjectRequest, ProjectResourceAllocationRequest, Resource, Space } from '../../api/types'
import { ProjectResourcesPanel } from './ProjectResourcesPanel'
import type { EffectiveAccess } from '../../api/types'
import { canProject, canSpace } from '../../api/accessPermissions'

const emptyForm: ProjectRequest = { spaceId: '', name: '', description: '', startDate: '', endDate: '' }
type ResourceDraft = { quantity: string; amount: string }

export function ProjectsManager({ onChange, access }: { onChange: () => void; access: EffectiveAccess | null }) {
  const [spaces, setSpaces] = useState<Space[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [form, setForm] = useState<ProjectRequest>(emptyForm)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [resourcesProjectId, setResourcesProjectId] = useState<string | null>(null)
  const [addResources, setAddResources] = useState(false)
  const [poolResources, setPoolResources] = useState<Resource[]>([])
  const [resourceDrafts, setResourceDrafts] = useState<Record<string, ResourceDraft>>({})
  const [resourcesLoading, setResourcesLoading] = useState(false)

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([spaceService.list({ signal: controller.signal }), projectService.list({ signal: controller.signal })])
      .then(([spaceData, projectData]) => { setSpaces(spaceData); setProjects(projectData) })
      .catch(cause => { if (!controller.signal.aborted) setError(getApiErrorMessage(cause, 'Impossible de charger les espaces et projets.')) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    setPoolResources([]); setResourceDrafts({})
    setResourcesLoading(false)
    if (!addResources || !form.spaceId || !canSpace(access, form.spaceId, 'project.resources.manage')) return
    let active = true
    setResourcesLoading(true)
    resourceService.listForSpace(form.spaceId).then(data => {
      if (!active) return
      setPoolResources(data)
      setResourceDrafts({})
    }).catch(cause => { if (active) setError(getApiErrorMessage(cause, 'Impossible de charger le pool de ressources de cet espace.')) })
      .finally(() => { if (active) setResourcesLoading(false) })
    return () => { active = false }
  }, [addResources, form.spaceId, access])

  useEffect(() => {
    if (!resourcesProjectId) return
    requestAnimationFrame(() => document.getElementById(`project-resource-panel-${resourcesProjectId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' }))
  }, [resourcesProjectId])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setError('')
    try {
      const saved = editingId ? await projectService.update(editingId, form) : await projectService.create(form)
      if (!editingId && addResources) {
        const selected = poolResources.filter(resource => resourceDrafts[resource.id])
        const results = await Promise.allSettled(selected.map(resource => {
          const draft = resourceDrafts[resource.id]
          const allocation: ProjectResourceAllocationRequest = resource.type === 'EQUIPMENT'
            ? { resourceId: resource.id, quantity: Number(draft.quantity) }
            : resource.type === 'BUDGET' ? { resourceId: resource.id, amount: Number(draft.amount) } : { resourceId: resource.id }
          return projectResourceService.assign(saved.id, allocation)
        }))
        const failed = results.filter(result => result.status === 'rejected').length
        if (failed) setError(`Le projet est créé, mais ${failed} ressource(s) n’ont pas pu être ajoutées. Ouvrez Ressources pour terminer l’affectation.`)
        setResourcesProjectId(saved.id)
      }
      setProjects(current => editingId ? current.map(item => item.id === saved.id ? saved : item) : [...current, saved].sort((a, b) => a.name.localeCompare(b.name)))
      setForm(emptyForm); setEditingId(null); setAddResources(false); setPoolResources([]); setResourceDrafts({}); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’enregistrer ce projet.')) }
    finally { setSaving(false) }
  }

  function edit(project: Project) {
    setForm({ spaceId: project.spaceId, name: project.name, description: project.description ?? '', startDate: project.startDate ?? '', endDate: project.endDate ?? '' })
    setEditingId(project.id); setResourcesProjectId(canManageResources(project) ? project.id : null); setAddResources(false); setError('')
  }

  async function remove(project: Project) {
    if (!window.confirm(`Supprimer le projet « ${project.name} » ?`)) return
    setSaving(true); setError('')
    try {
      await projectService.remove(project.id)
      setProjects(current => current.filter(item => item.id !== project.id)); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Suppression impossible. Ce projet contient peut-être des tâches.')) }
    finally { setSaving(false) }
  }

  const spaceName = (id: string) => spaces.find(space => space.id === id)?.name ?? 'Espace indisponible'
  const canCreate = canSpace(access, form.spaceId, 'project.create')
  const canEdit = (project: Project) => canProject(access, project.id, project.spaceId, 'project.edit')
  const canDelete = (project: Project) => canProject(access, project.id, project.spaceId, 'project.delete')
  const canManageResources = (project: Project) => canProject(access, project.id, project.spaceId, 'project.resources.manage')
  const selectedResources = poolResources.filter(resource => !!resourceDrafts[resource.id])
  const invalidResourceDraft = selectedResources.some(resource => {
    const draft = resourceDrafts[resource.id]
    if (resource.status !== 'AVAILABLE') return true
    if (resource.type === 'EQUIPMENT') return !Number.isInteger(Number(draft.quantity)) || Number(draft.quantity) < 1 || Number(draft.quantity) > (resource.availableQuantity ?? 0)
    if (resource.type === 'BUDGET') return !Number.isFinite(Number(draft.amount)) || Number(draft.amount) < 0.01 || Number(draft.amount) > (resource.availableAmount ?? 0)
    return false
  })
  const showForm = spaces.some(space => canSpace(access, space.id, 'project.create')) || (!!editingId && projects.some(project => project.id === editingId && canEdit(project)))
  const assignableSpaces = spaces.filter(space => canSpace(access, space.id, 'project.create') || (!!editingId && projects.some(project => project.id === editingId && project.spaceId === space.id && canEdit(project))))

  return <div className="crud-layout">
    {showForm && <section className="form-panel crud-form-panel">
      <div className="form-panel-heading"><div className="form-icon">P</div><div><h2>{editingId ? 'Modifier le projet' : 'Nouveau projet'}</h2><p>Chaque projet appartient à un espace.</p></div></div>
      <form className="organization-form" onSubmit={submit}><div className="form-grid">
        <div className="form-field full-width"><label htmlFor="project-space">Espace <span>*</span></label><select id="project-space" required value={form.spaceId} onChange={event => { setForm({ ...form, spaceId: event.target.value }); setAddResources(false) }}><option value="">Choisir un espace</option>{assignableSpaces.map(space => <option key={space.id} value={space.id}>{space.name}</option>)}</select></div>
        <div className="form-field full-width"><label htmlFor="project-name">Nom du projet <span>*</span></label><input id="project-name" required maxLength={120} value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} /></div>
        <div className="form-field full-width"><label htmlFor="project-description">Description</label><textarea id="project-description" maxLength={10000} rows={3} value={form.description ?? ''} onChange={event => setForm({ ...form, description: event.target.value })} /></div>
        <div className="form-field"><label htmlFor="project-start">Date de début</label><input id="project-start" type="date" value={form.startDate ?? ''} onChange={event => setForm({ ...form, startDate: event.target.value })} /></div>
        <div className="form-field"><label htmlFor="project-end">Date de fin</label><input id="project-end" type="date" value={form.endDate ?? ''} onChange={event => setForm({ ...form, endDate: event.target.value })} /></div>
      </div>
      {!editingId && canSpace(access, form.spaceId, 'project.resources.manage') && <div className="project-create-resources"><label className="project-resource-toggle"><input type="checkbox" checked={addResources} onChange={event => setAddResources(event.target.checked)} /><span><strong>Ajouter des ressources à la création</strong><small>Vous pourrez choisir des employés, équipements et budgets du pool de cet espace.</small></span></label>
        {addResources && <div className="project-create-resource-list">{resourcesLoading ? <p className="field-hint">Chargement du pool…</p> : poolResources.length ? poolResources.map(resource => { const unavailable = resource.status !== 'AVAILABLE' || resource.type === 'EQUIPMENT' && !(resource.availableQuantity ?? 0) || resource.type === 'BUDGET' && !(resource.availableAmount ?? 0); return <label className={`project-create-resource ${unavailable ? 'unavailable' : ''}`} key={resource.id}><input type="checkbox" disabled={unavailable} checked={!!resourceDrafts[resource.id]} onChange={event => setResourceDrafts(current => { const next = { ...current }; if (event.target.checked) next[resource.id] = { quantity: '1', amount: '' }; else delete next[resource.id]; return next })} /><span className="project-create-resource-name"><strong>{resource.name}</strong><small>{resource.type === 'HUMAN' ? `Employé${resource.jobTitle ? ` · ${resource.jobTitle}` : ''}` : resource.type === 'EQUIPMENT' ? `Équipement · ${resource.availableQuantity ?? 0} unité(s) disponibles` : `Budget · ${resource.availableAmount ?? 0} ${resource.currency ?? ''} disponibles`}{unavailable ? ' · Indisponible' : ''}</small></span>{resourceDrafts[resource.id] && resource.type === 'EQUIPMENT' && <input className="resource-allocation-input" aria-label={`Unités pour ${resource.name}`} type="number" min="1" max={resource.availableQuantity ?? undefined} step="1" value={resourceDrafts[resource.id].quantity} onChange={event => setResourceDrafts(current => ({ ...current, [resource.id]: { ...current[resource.id], quantity: event.target.value } }))} />}{resourceDrafts[resource.id] && resource.type === 'BUDGET' && <input className="resource-allocation-input" aria-label={`Montant pour ${resource.name}`} type="number" min="0.01" max={resource.availableAmount ?? undefined} step="0.01" value={resourceDrafts[resource.id].amount} onChange={event => setResourceDrafts(current => ({ ...current, [resource.id]: { ...current[resource.id], amount: event.target.value } }))} />}</label>}) : <p className="field-hint">{form.spaceId ? 'Aucune ressource disponible dans le pool de cet espace.' : 'Choisissez un espace pour afficher son pool.'}</p>}</div>}
      </div>}
      {error && <div className="alert form-alert" role="alert">{error}</div>}{spaces.length === 0 && <p className="field-hint">Créez un espace avant d’ajouter un projet.</p>}
      <div className="form-actions"><button className="primary-button" disabled={saving || spaces.length === 0 || (!editingId && !canCreate) || resourcesLoading || invalidResourceDraft}>{saving ? 'Enregistrement…' : editingId ? 'Enregistrer' : 'Créer le projet'}</button>{editingId && <button type="button" className="secondary-button" onClick={() => { setEditingId(null); setResourcesProjectId(null); setForm(emptyForm) }}>Annuler</button>}</div></form>
    </section>}
    <section className="crud-list-panel" aria-live="polite">
      {loading ? <div className="empty-state">Chargement des projets…</div> : projects.length ? projects.map(project => <article className="crud-row" key={project.id}><div className="crud-symbol blue">P</div><div className="crud-row-main"><strong>{project.name}</strong><small>{spaceName(project.spaceId)}{project.startDate ? ` · ${project.startDate}` : ''}</small></div><div className="crud-actions">{canEdit(project) && <button type="button" className="secondary-button" disabled={saving} onClick={() => edit(project)}>Modifier</button>}{canManageResources(project) && <button type="button" className="secondary-button" disabled={saving} onClick={() => setResourcesProjectId(current => current === project.id ? null : project.id)}>Ressources</button>}{canDelete(project) && <button type="button" className="danger-button" disabled={saving} onClick={() => void remove(project)}>Supprimer</button>}</div></article>) : <div className="empty-state"><strong>Aucun projet</strong><p>Choisissez un espace pour créer votre premier projet.</p></div>}
    </section>
    {resourcesProjectId && projects.find(project => project.id === resourcesProjectId && canManageResources(project)) && <ProjectResourcesPanel key={resourcesProjectId} project={projects.find(project => project.id === resourcesProjectId)!} onClose={() => setResourcesProjectId(null)} />}
  </div>
}
