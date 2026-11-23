import { useEffect, useState, type FormEvent } from 'react'
import { organizationService } from '../../api/organizationService'
import { getApiErrorMessage } from '../../api/http'
import type { Organization, OrganizationUpdateRequest } from '../../api/types'

const empty: OrganizationUpdateRequest = { name: '', description: '', website: '', contactEmail: '', phone: '' }

export function OrganizationSettings({ organizationId, onSaved }: { organizationId: string; onSaved: (organization: Organization) => void }) {
  const [form, setForm] = useState<OrganizationUpdateRequest>(empty)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    organizationService.get(organizationId, { signal: controller.signal }).then(org => setForm({
      name: org.name, description: org.description ?? '', website: org.website ?? '', contactEmail: org.contactEmail ?? '', phone: org.phone ?? '',
    })).catch(cause => { if (!controller.signal.aborted) setError(getApiErrorMessage(cause, 'Impossible de charger les paramètres.')) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [organizationId])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setError(''); setMessage('')
    try {
      const saved = await organizationService.update(organizationId, form)
      onSaved(saved); setMessage('Les paramètres de l’organisation ont été enregistrés.')
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’enregistrer les paramètres.')) }
    finally { setSaving(false) }
  }

  if (loading) return <section className="form-panel"><p>Chargement des paramètres…</p></section>
  return <section className="form-panel"><div className="form-panel-heading"><div><h2>Paramètres de l’organisation</h2><p>Nom, coordonnées et description visibles par votre équipe.</p></div></div>
    <form className="organization-form" onSubmit={submit}><div className="form-grid">
      <div className="form-field full-width"><label htmlFor="org-settings-name">Nom <span>*</span></label><input id="org-settings-name" required maxLength={50} value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} /></div>
      <div className="form-field full-width"><label htmlFor="org-settings-description">Description</label><textarea id="org-settings-description" maxLength={500} value={form.description ?? ''} onChange={event => setForm({ ...form, description: event.target.value })} /></div>
      <div className="form-field"><label htmlFor="org-settings-website">Site web</label><input id="org-settings-website" type="url" maxLength={255} value={form.website ?? ''} onChange={event => setForm({ ...form, website: event.target.value })} /></div>
      <div className="form-field"><label htmlFor="org-settings-email">E-mail de contact</label><input id="org-settings-email" type="email" maxLength={254} value={form.contactEmail ?? ''} onChange={event => setForm({ ...form, contactEmail: event.target.value })} /></div>
      <div className="form-field"><label htmlFor="org-settings-phone">Téléphone</label><input id="org-settings-phone" type="tel" maxLength={30} value={form.phone ?? ''} onChange={event => setForm({ ...form, phone: event.target.value })} /></div>
    </div>{error && <div className="alert form-alert" role="alert">{error}</div>}{message && <div className="success-message" role="status">{message}</div>}<div className="form-actions"><button className="primary-button" disabled={saving}>{saving ? 'Enregistrement…' : 'Enregistrer'}</button></div></form>
  </section>
}
