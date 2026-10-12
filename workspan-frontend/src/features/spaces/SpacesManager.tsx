import { useEffect, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '../../api/http'
import { spaceService } from '../../api/spaceService'
import type { Space, SpaceRequest } from '../../api/types'

const emptyForm: SpaceRequest = { name: '' }

export function SpacesManager({ onChange }: { onChange: () => void }) {
  const [spaces, setSpaces] = useState<Space[]>([])
  const [name, setName] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    spaceService.list({ signal: controller.signal }).then(setSpaces).catch(cause => {
      if (!controller.signal.aborted) setError(getApiErrorMessage(cause, 'Impossible de charger les espaces.'))
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setError('')
    try {
      const request = { ...emptyForm, name: name.trim() }
      const saved = editingId ? await spaceService.update(editingId, request) : await spaceService.create(request)
      setSpaces(current => editingId ? current.map(item => item.id === saved.id ? saved : item) : [...current, saved].sort((a, b) => a.name.localeCompare(b.name)))
      setName(''); setEditingId(null); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’enregistrer cet espace.')) }
    finally { setSaving(false) }
  }

  function edit(space: Space) { setName(space.name); setEditingId(space.id); setError('') }
  function cancelEdit() { setName(''); setEditingId(null) }

  async function remove(space: Space) {
    if (!window.confirm(`Supprimer l’espace « ${space.name} » ?`)) return
    setSaving(true); setError('')
    try {
      await spaceService.remove(space.id)
      setSpaces(current => current.filter(item => item.id !== space.id)); onChange()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Suppression impossible. Cet espace contient peut-être des projets.')) }
    finally { setSaving(false) }
  }

  return <div className="crud-layout">
    <section className="form-panel crud-form-panel">
      <div className="form-panel-heading"><div className="form-icon">E</div><div><h2>{editingId ? 'Modifier l’espace' : 'Nouvel espace'}</h2><p>Les espaces regroupent les projets de votre organisation.</p></div></div>
      <form className="organization-form" onSubmit={submit}><div className="form-field"><label htmlFor="space-name">Nom de l’espace <span>*</span></label><input id="space-name" required maxLength={50} value={name} onChange={event => setName(event.target.value)} /></div>
        {error && <div className="alert form-alert" role="alert">{error}</div>}
        <div className="form-actions"><button className="primary-button" disabled={saving}>{saving ? 'Enregistrement…' : editingId ? 'Enregistrer' : 'Créer l’espace'}</button>{editingId && <button type="button" className="secondary-button" onClick={cancelEdit}>Annuler</button>}</div>
      </form>
    </section>
    <section className="crud-list-panel" aria-live="polite">
      {loading ? <div className="empty-state">Chargement des espaces…</div> : spaces.length ? spaces.map(space => <article className="crud-row" key={space.id}><div className="crud-symbol violet">E</div><div className="crud-row-main"><strong>{space.name}</strong><small>Créé le {new Date(space.createdAt).toLocaleDateString('fr-FR')}</small></div><div className="crud-actions"><button className="secondary-button" disabled={saving} onClick={() => edit(space)}>Modifier</button><button className="danger-button" disabled={saving} onClick={() => void remove(space)}>Supprimer</button></div></article>) : <div className="empty-state"><strong>Aucun espace</strong><p>Créez votre premier espace pour commencer.</p></div>}
    </section>
  </div>
}
