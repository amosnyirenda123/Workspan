import { useEffect, useMemo, useState, type FormEvent } from 'react'
import './App.css'

type Organization = {
  id: string
  name: string
  description: string | null
  website: string | null
  contactEmail: string | null
  phone: string | null
  spaces: number
  projects: number
  tasks: number
  createdAt: string
}

type ApiError = { detail?: string; message?: string }
type OrganizationForm = { name: string; description: string; website: string; contactEmail: string; phone: string }

const emptyOrganizationForm: OrganizationForm = { name: '', description: '', website: '', contactEmail: '', phone: '' }

function App() {
  const [pathname, setPathname] = useState(() => window.location.pathname)
  const [organizations, setOrganizations] = useState<Organization[]>([])
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [search, setSearch] = useState('')
  const [organizationForm, setOrganizationForm] = useState<OrganizationForm>(emptyOrganizationForm)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState('')
  const [detail, setDetail] = useState<Organization | null>(null)
  const [detailError, setDetailError] = useState<{ id: string; message: string } | null>(null)

  const detailId = pathname === '/organizations/new' ? null : /^\/organizations\/([^/]+)$/.exec(pathname)?.[1] ?? null
  const isCreatePage = pathname === '/organizations/new'
  const isOrganizationsPage = pathname === '/' || pathname === '/organizations'

  function navigate(path: string) {
    window.history.pushState({}, '', path)
    setPathname(path)
    setFormError('')
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  useEffect(() => {
    const onPopState = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  async function loadOrganizations() {
    setListLoading(true)
    try {
      const response = await fetch('/api/organizations')
      if (!response.ok) throw new Error('Impossible de charger les organisations.')
      setOrganizations(await response.json() as Organization[])
      setListError('')
    } catch (cause) {
      setListError(cause instanceof Error ? cause.message : 'Le serveur est indisponible.')
    } finally {
      setListLoading(false)
    }
  }

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/organizations', { signal: controller.signal })
      .then(async response => {
        if (!response.ok) throw new Error('Impossible de charger les organisations.')
        return await response.json() as Organization[]
      })
      .then(data => {
        setOrganizations(data)
        setListError('')
      })
      .catch(cause => {
        if (!controller.signal.aborted) setListError(cause instanceof Error ? cause.message : 'Le serveur est indisponible.')
      })
      .finally(() => {
        if (!controller.signal.aborted) setListLoading(false)
      })
    return () => controller.abort()
  }, [])

  useEffect(() => {
    if (!detailId) return

    const controller = new AbortController()
    fetch(`/api/organizations/${encodeURIComponent(detailId)}`, { signal: controller.signal })
      .then(async response => {
        if (!response.ok) {
          if (response.status === 404) throw new Error('Cette organisation est introuvable.')
          throw new Error('Impossible de charger les détails de l’organisation.')
        }
        return await response.json() as Organization
      })
      .then(setDetail)
      .catch(cause => {
        if (!controller.signal.aborted) setDetailError({
          id: detailId,
          message: cause instanceof Error ? cause.message : 'Une erreur est survenue.',
        })
      })
    return () => controller.abort()
  }, [detailId])

  async function createOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const name = organizationForm.name.trim()
    if (!name) return

    setSaving(true)
    setFormError('')
    try {
      const response = await fetch('/api/organizations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name,
          description: organizationForm.description.trim() || null,
          website: organizationForm.website.trim() || null,
          contactEmail: organizationForm.contactEmail.trim() || null,
          phone: organizationForm.phone.trim() || null,
        }),
      })
      if (!response.ok) {
        const body = await response.json().catch(() => ({})) as ApiError
        throw new Error(body.detail ?? body.message ?? 'Impossible de créer cette organisation.')
      }
      const created = await response.json() as Organization
      setOrganizationForm(emptyOrganizationForm)
      await loadOrganizations()
      navigate(`/organizations/${created.id}`)
    } catch (cause) {
      setFormError(cause instanceof Error ? cause.message : 'Impossible de créer cette organisation.')
    } finally {
      setSaving(false)
    }
  }

  const totals = useMemo(() => organizations.reduce((sum, organization) => ({
    spaces: sum.spaces + organization.spaces,
    projects: sum.projects + organization.projects,
    tasks: sum.tasks + organization.tasks,
  }), { spaces: 0, projects: 0, tasks: 0 }), [organizations])

  const filteredOrganizations = organizations.filter(organization => organization.name.toLowerCase().includes(search.trim().toLowerCase()))
  const currentDetail = detail?.id === detailId ? detail : null
  const currentDetailError = detailError?.id === detailId ? detailError.message : ''
  const today = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date())
  const pageTitle = isCreatePage ? 'Nouvelle organisation' : detailId ? 'Détail de l’organisation' : 'Organisations'

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="/" onClick={event => { event.preventDefault(); navigate('/') }}><span className="brand-mark">W</span> Workspan</a>
        <p className="nav-label">ESPACE DE TRAVAIL</p>
        <a className={`nav-item ${isOrganizationsPage ? 'active' : ''}`} href="/organizations" onClick={event => { event.preventDefault(); navigate('/organizations') }}><span>◫</span> Organisations</a>
        <div className="sidebar-bottom"><div className="avatar">W</div><div><strong>Workspan</strong><small>Gestion de projets</small></div></div>
      </aside>

      <section className="main-panel">
        <header className="topbar"><span>Workspan <b>/</b> {pageTitle}</span><span className="connection"><i /> API</span></header>
        <div className="content">
          {isOrganizationsPage && <>
            <div className="page-heading"><div><p className="eyebrow">{today}</p><h1>Bonjour ! <span>✳</span></h1><p className="subtitle">Bienvenue dans votre espace de travail Workspan.</p></div><button className="primary-button" onClick={() => navigate('/organizations/new')}>＋ <span>Nouvelle organisation</span></button></div>

            {listError && <div className="alert" role="alert">{listError} <button className="text-button" onClick={() => void loadOrganizations()}>Réessayer</button></div>}

            <section className="stats-grid" aria-label="Résumé de l’espace de travail">
              <article className="stat-card"><div className="stat-icon violet">◫</div><p>Organisations</p><strong>{organizations.length}</strong><small>Dans votre espace</small></article>
              <article className="stat-card"><div className="stat-icon blue">▦</div><p>Espaces</p><strong>{totals.spaces}</strong><small>Pour vos équipes</small></article>
              <article className="stat-card"><div className="stat-icon green">◈</div><p>Projets</p><strong>{totals.projects}</strong><small>En cours de suivi</small></article>
              <article className="stat-card"><div className="stat-icon orange">☑</div><p>Tâches</p><strong>{totals.tasks}</strong><small>Dans les projets</small></article>
            </section>

            <section className="organizations-panel">
              <div className="section-heading"><div><h2>Vos organisations</h2><p>Consultez vos organisations et ouvrez leur fiche détaillée.</p></div><span className="count-pill">{organizations.length} au total</span></div>
              <div className="list-toolbar"><label className="search-box"><span>⌕</span><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Rechercher une organisation" aria-label="Rechercher une organisation" /></label><button className="secondary-button" onClick={() => void loadOrganizations()}>↻ Actualiser</button></div>
              {listLoading ? <div className="empty-state">Chargement des organisations…</div> : organizations.length === 0 ? <div className="empty-state"><div className="empty-icon">◫</div><strong>Aucune organisation pour le moment</strong><p>Créez votre première organisation pour commencer.</p><button className="primary-button" onClick={() => navigate('/organizations/new')}>＋ Créer une organisation</button></div> : filteredOrganizations.length === 0 ? <div className="empty-state"><strong>Aucun résultat</strong><p>Essayez un autre nom d’organisation.</p></div> : <div className="org-list">{filteredOrganizations.map(organization => <button className="org-row" key={organization.id} onClick={() => navigate(`/organizations/${organization.id}`)}><div className="org-mark">{organization.name.charAt(0).toUpperCase()}</div><div className="org-name"><strong>{organization.name}</strong><small>Créée le {new Date(organization.createdAt).toLocaleDateString('fr-FR')}</small></div><div className="org-metric"><strong>{organization.spaces}</strong><small>Espaces</small></div><div className="org-metric"><strong>{organization.projects}</strong><small>Projets</small></div><div className="org-metric"><strong>{organization.tasks}</strong><small>Tâches</small></div><span className="row-arrow">›</span></button>)}</div>}
            </section>
          </>}

          {isCreatePage && <>
            <button className="back-link" onClick={() => navigate('/organizations')}>← Retour aux organisations</button>
            <div className="page-heading compact-heading"><div><p className="eyebrow">ESPACE DE TRAVAIL</p><h1>Créer une organisation</h1><p className="subtitle">Une organisation regroupe ses espaces, projets et équipes.</p></div></div>
            <section className="form-panel"><div className="form-panel-heading"><div className="form-icon">◫</div><div><h2>Informations générales</h2><p>Renseignez le nom de votre nouvelle organisation.</p></div></div>
              <form className="organization-form" onSubmit={createOrganization}>
                <div className="form-grid">
                  <div className="form-field full-width"><label htmlFor="organization-name">Nom de l’organisation <span>*</span></label><input id="organization-name" value={organizationForm.name} onChange={event => setOrganizationForm({ ...organizationForm, name: event.target.value })} placeholder="Ex. Acme Studio" maxLength={50} autoFocus required /><small className="field-hint">50 caractères maximum.</small></div>
                  <div className="form-field full-width"><label htmlFor="organization-description">Description</label><textarea id="organization-description" value={organizationForm.description} onChange={event => setOrganizationForm({ ...organizationForm, description: event.target.value })} placeholder="Présentez brièvement votre organisation" maxLength={500} rows={4} /><small className="field-hint">Facultatif · 500 caractères maximum.</small></div>
                  <div className="form-field"><label htmlFor="organization-website">Site web</label><input id="organization-website" type="url" value={organizationForm.website} onChange={event => setOrganizationForm({ ...organizationForm, website: event.target.value })} placeholder="https://exemple.com" maxLength={255} /></div>
                  <div className="form-field"><label htmlFor="organization-email">E-mail de contact</label><input id="organization-email" type="email" value={organizationForm.contactEmail} onChange={event => setOrganizationForm({ ...organizationForm, contactEmail: event.target.value })} placeholder="contact@exemple.com" maxLength={254} /></div>
                  <div className="form-field"><label htmlFor="organization-phone">Téléphone</label><input id="organization-phone" type="tel" value={organizationForm.phone} onChange={event => setOrganizationForm({ ...organizationForm, phone: event.target.value })} placeholder="+212 600 000 000" maxLength={30} /></div>
                </div>
                {formError && <div className="alert form-alert" role="alert">{formError}</div>}
                <div className="form-actions"><button type="button" className="secondary-button" onClick={() => navigate('/organizations')}>Annuler</button><button className="primary-button" type="submit" disabled={saving}>{saving ? 'Création en cours…' : 'Créer l’organisation'}</button></div>
              </form>
            </section>
          </>}

          {detailId && <>
            <button className="back-link" onClick={() => navigate('/organizations')}>← Retour aux organisations</button>
            {!currentDetail && !currentDetailError ? <section className="form-panel detail-loading">Chargement de la fiche organisation…</section> : currentDetailError ? <section className="form-panel detail-error"><div className="alert" role="alert">{currentDetailError}</div><button className="secondary-button" onClick={() => navigate('/organizations')}>Retour à la liste</button></section> : currentDetail && <>
              <div className="detail-heading"><div className="detail-avatar">{currentDetail.name.charAt(0).toUpperCase()}</div><div className="detail-title"><p className="eyebrow">FICHE ORGANISATION</p><h1>{currentDetail.name}</h1><p className="subtitle">Créée le {new Date(currentDetail.createdAt).toLocaleDateString('fr-FR', { dateStyle: 'long' })}</p></div><span className="type-pill">Organisation</span></div>
              <section className="detail-grid" aria-label="Récapitulatif de l’organisation">
                <article className="detail-card"><div className="stat-icon blue">▦</div><p>Espaces</p><strong>{currentDetail.spaces}</strong><small>Dans cette organisation</small></article>
                <article className="detail-card"><div className="stat-icon green">◈</div><p>Projets</p><strong>{currentDetail.projects}</strong><small>Dans tous les espaces</small></article>
                <article className="detail-card"><div className="stat-icon orange">☑</div><p>Tâches</p><strong>{currentDetail.tasks}</strong><small>Dans tous les projets</small></article>
              </section>
              <section className="form-panel metadata-panel"><div className="section-heading"><div><h2>Informations</h2><p>Détails enregistrés pour cette organisation.</p></div></div><dl className="metadata-list"><div><dt>Nom</dt><dd>{currentDetail.name}</dd></div>{currentDetail.description && <div><dt>Description</dt><dd>{currentDetail.description}</dd></div>}{currentDetail.website && <div><dt>Site web</dt><dd><a href={currentDetail.website} target="_blank" rel="noreferrer">{currentDetail.website}</a></dd></div>}{currentDetail.contactEmail && <div><dt>E-mail de contact</dt><dd><a href={`mailto:${currentDetail.contactEmail}`}>{currentDetail.contactEmail}</a></dd></div>}{currentDetail.phone && <div><dt>Téléphone</dt><dd><a href={`tel:${currentDetail.phone}`}>{currentDetail.phone}</a></dd></div>}<div><dt>Identifiant</dt><dd className="id-value">{currentDetail.id}</dd></div><div><dt>Date de création</dt><dd>{new Date(currentDetail.createdAt).toLocaleString('fr-FR')}</dd></div></dl></section>
            </>}
          </>}

          <footer>© 2026 Workspan <span>·</span> Pensé pour les équipes qui font avancer leurs projets.</footer>
        </div>
      </section>
    </main>
  )
}

export default App
