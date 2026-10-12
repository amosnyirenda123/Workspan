import { useEffect, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '../../api/http'
import { projectService } from '../../api/projectService'
import { spaceService } from '../../api/spaceService'
import type { Project, ProjectRequest, Space } from '../../api/types'

const emptyForm: ProjectRequest = { spaceId: '', name: '', description: '', startDate: '', endDate: '' }

export function ProjectsManager({ onChange }: { onChange: () => void }) {
  const [spaces, setSpaces] = useState<Space[]>([])
  const [projects, setProjects] = useState<Project[]>([])
  const [form, setForm] = useState<ProjectRequest>(emptyForm)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([spaceService.list({ signal: controller.signal }), projectService.list({ signal: controller.signal })])
      .then(([spaceData, projectData]) => { setSpaces(spaceData); setProjects(projectData) })
      .catch(cause => { if (!controller.signal.aborted) setError(getApiErrorMessage(cause, 'Impossible de charger les espaces et projets.')) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setError('')
    try {
      const saved = editingId ? await projectService.update(editingId, form) : await projectService.create(form)
      setProjects(current => editingId ? current.map(item => item.id === saved.id ? saved : item) : [...current, saved].sort((a, b) => a.name.localeCompare(b.name)))
      setForm(emptyForm); setEditingId(null); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’enregistrer ce projet.')) }
    finally { setSaving(false) }
  }

  function edit(project: Project) {
    setForm({ spaceId: project.spaceId, name: project.name, description: project.description ?? '', startDate: project.startDate ?? '', endDate: project.endDate ?? '' })
    setEditingId(project.id); setError('')
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

  return <div className="crud-layout">
    <section className="form-panel crud-form-panel">
      <div className="form-panel-heading"><div className="form-icon">P</div><div><h2>{editingId ? 'Modifier le projet' : 'Nouveau projet'}</h2><p>Chaque projet appartient à un espace.</p></div></div>
      <form className="organization-form" onSubmit={submit}><div className="form-grid">
        <div className="form-field full-width"><label htmlFor="project-space">Espace <span>*</span></label><select id="project-space" required value={form.spaceId} onChange={event => setForm({ ...form, spaceId: event.target.value })}><option value="">Choisir un espace</option>{spaces.map(space => <option key={space.id} value={space.id}>{space.name}</option>)}</select></div>
        <div className="form-field full-width"><label htmlFor="project-name">Nom du projet <span>*</span></label><input id="project-name" required maxLength={120} value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} /></div>
        <div className="form-field full-width"><label htmlFor="project-description">Description</label><textarea id="project-description" maxLength={10000} rows={3} value={form.description ?? ''} onChange={event => setForm({ ...form, description: event.target.value })} /></div>
        <div className="form-field"><label htmlFor="project-start">Date de début</label><input id="project-start" type="date" value={form.startDate ?? ''} onChange={event => setForm({ ...form, startDate: event.target.value })} /></div>
        <div className="form-field"><label htmlFor="project-end">Date de fin</label><input id="project-end" type="date" value={form.endDate ?? ''} onChange={event => setForm({ ...form, endDate: event.target.value })} /></div>
      </div>
      {error && <div className="alert form-alert" role="alert">{error}</div>}{spaces.length === 0 && <p className="field-hint">Créez un espace avant d’ajouter un projet.</p>}
      <div className="form-actions"><button className="primary-button" disabled={saving || spaces.length === 0}>{saving ? 'Enregistrement…' : editingId ? 'Enregistrer' : 'Créer le projet'}</button>{editingId && <button type="button" className="secondary-button" onClick={() => { setEditingId(null); setForm(emptyForm) }}>Annuler</button>}</div></form>
    </section>
    <section className="crud-list-panel" aria-live="polite">
      {loading ? <div className="empty-state">Chargement des projets…</div> : projects.length ? projects.map(project => <article className="crud-row" key={project.id}><div className="crud-symbol blue">P</div><div className="crud-row-main"><strong>{project.name}</strong><small>{spaceName(project.spaceId)}{project.startDate ? ` · ${project.startDate}` : ''}</small></div><div className="crud-actions"><button className="secondary-button" disabled={saving} onClick={() => edit(project)}>Modifier</button><button className="danger-button" disabled={saving} onClick={() => void remove(project)}>Supprimer</button></div></article>) : <div className="empty-state"><strong>Aucun projet</strong><p>Choisissez un espace pour créer votre premier projet.</p></div>}
    </section>
  </div>
}
