import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { authService } from './api/authService'
import { getApiErrorMessage } from './api/http'
import { organizationService } from './api/organizationService'
import type { AuthUser, Organization } from './api/types'
import './App.css'

type AuthForm = { organizationName: string; displayName: string; email: string; password: string }


function App() {
  const [pathname, setPathname] = useState(() => window.location.pathname)
  const [organizations, setOrganizations] = useState<Organization[]>([])
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [search, setSearch] = useState('')
  const [detail, setDetail] = useState<Organization | null>(null)
  const [detailError, setDetailError] = useState<{ id: string; message: string } | null>(null)
  const [authForm, setAuthForm] = useState<AuthForm>({ organizationName: '', displayName: '', email: '', password: '' })
  const [authError, setAuthError] = useState('')
  const [authSaving, setAuthSaving] = useState(false)
  const [authReady, setAuthReady] = useState(false)
  const [authUser, setAuthUser] = useState<AuthUser | null>(null)

  const detailId = pathname === '/organizations/new' ? null : /^\/organizations\/([^/]+)$/.exec(pathname)?.[1] ?? null
  const isOrganizationsPage = pathname === '/organizations'

  function navigate(path: string) {
    window.history.pushState({}, '', path)
    setPathname(path)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  useEffect(() => {
    const onPopState = () => setPathname(window.location.pathname)
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  useEffect(() => {
    let active = true
    authService.me()
      .then(user => { if (active) setAuthUser(user) })
      .catch(() => { if (active) setAuthUser(null) })
      .finally(() => { if (active) setAuthReady(true) })
    return () => { active = false }
  }, [])

  useEffect(() => {
    if (!authReady) return
    if (authUser && pathname === '/') window.location.replace('/organizations')
    else if (!authUser && pathname !== '/' && pathname !== '/login' && pathname !== '/register') window.location.replace('/login')
    else if (authUser && (pathname === '/login' || pathname === '/register')) window.location.replace('/organizations')
    else if (authUser && pathname === '/organizations/new') window.location.replace('/organizations')
  }, [authReady, authUser, pathname])

  async function loadOrganizations() {
    setListLoading(true)
    try {
      setOrganizations(await organizationService.list())
      setListError('')
    } catch (cause) {
      setListError(getApiErrorMessage(cause, 'Impossible de charger les organisations.'))
    } finally {
      setListLoading(false)
    }
  }

  useEffect(() => {
    if (!authUser || !isOrganizationsPage) return
    const controller = new AbortController()
    organizationService.list({ signal: controller.signal })
      .then(data => {
        setOrganizations(data)
        setListError('')
      })
      .catch(cause => {
        if (!controller.signal.aborted) setListError(getApiErrorMessage(cause, 'Impossible de charger les organisations.'))
      })
      .finally(() => {
        if (!controller.signal.aborted) setListLoading(false)
      })
    return () => controller.abort()
  }, [authUser, isOrganizationsPage])

  useEffect(() => {
    if (!detailId || !authUser) return

    const controller = new AbortController()
    organizationService.get(detailId, { signal: controller.signal })
      .then(setDetail)
      .catch(cause => {
        if (!controller.signal.aborted) setDetailError({
          id: detailId,
          message: getApiErrorMessage(cause, 'Impossible de charger cette organisation.'),
        })
      })
    return () => controller.abort()
  }, [detailId, authUser])

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const registering = pathname === '/register'
    setAuthSaving(true)
    setAuthError('')
    try {
      const user = registering ? await authService.register(authForm) : await authService.login({ email: authForm.email, password: authForm.password })
      setAuthUser(user)
      navigate('/organizations')
    } catch (cause) {
      setAuthError(getApiErrorMessage(cause, 'Impossible de vous connecter.'))
    } finally { setAuthSaving(false) }
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
  const pageTitle = detailId ? 'Fiche organisation' : 'Organisations'

  async function logout() {
    await authService.logout().catch(() => undefined)
    setAuthUser(null)
    setOrganizations([])
    navigate('/')
  }

  if (!authReady || (authUser === null && pathname.startsWith('/organizations'))) return <main className="app-shell"><section className="main-panel" style={{ margin: '12vh auto', maxWidth: 560 }}><div className="content">Chargement de votre espace…</div></section></main>

  if (pathname === '/' && !authUser) return <main className="app-shell"><section className="main-panel" style={{ maxWidth: 960, margin: '8vh auto', borderRadius: 18 }}><div className="content"><a className="brand" href="/" onClick={event => event.preventDefault()}><span className="brand-mark">W</span> Workspan</a><div className="page-heading compact-heading" style={{ marginTop: 72 }}><div><p className="eyebrow">PLANIFIEZ ENSEMBLE</p><h1>Tout votre travail, au même endroit.</h1><p className="subtitle">Créez votre espace Workspan, rassemblez vos équipes et suivez projets et tâches depuis une seule organisation.</p></div></div><div className="form-actions" style={{ justifyContent: 'flex-start' }}><button className="primary-button" onClick={() => navigate('/register')}>Créer un compte</button><button className="secondary-button" onClick={() => navigate('/login')}>Se connecter</button></div></div></section></main>

  if (pathname === '/register' || pathname === '/login') {
    const registering = pathname === '/register'
    return <main className="app-shell"><section className="main-panel" style={{ maxWidth: 560, margin: '8vh auto', borderRadius: 18 }}><div className="content"><a className="brand" href="/" onClick={event => { event.preventDefault(); navigate('/') }}><span className="brand-mark">W</span> Workspan</a><div className="page-heading compact-heading"><div><p className="eyebrow">ESPACE DE TRAVAIL</p><h1>{registering ? 'Créez votre espace' : 'Connexion'}</h1><p className="subtitle">{registering ? 'Votre organisation et votre compte administrateur seront créés ensemble.' : 'Connectez-vous à votre espace Workspan.'}</p></div></div><section className="form-panel"><form className="organization-form" onSubmit={submitAuth}><div className="form-grid">{registering && <><div className="form-field full-width"><label htmlFor="auth-org">Nom de l’organisation <span>*</span></label><input id="auth-org" required maxLength={50} value={authForm.organizationName} onChange={event => setAuthForm({ ...authForm, organizationName: event.target.value })} /></div><div className="form-field full-width"><label htmlFor="auth-name">Votre nom <span>*</span></label><input id="auth-name" required maxLength={100} value={authForm.displayName} onChange={event => setAuthForm({ ...authForm, displayName: event.target.value })} /></div></>}<div className="form-field full-width"><label htmlFor="auth-email">E-mail <span>*</span></label><input id="auth-email" type="email" required maxLength={254} autoComplete="email" value={authForm.email} onChange={event => setAuthForm({ ...authForm, email: event.target.value })} /></div><div className="form-field full-width"><label htmlFor="auth-password">Mot de passe <span>*</span></label><input id="auth-password" type="password" required minLength={10} autoComplete={registering ? 'new-password' : 'current-password'} value={authForm.password} onChange={event => setAuthForm({ ...authForm, password: event.target.value })} /><small className="field-hint">10 caractères minimum.</small></div></div>{authError && <div className="alert form-alert" role="alert">{authError}</div>}<div className="form-actions"><button className="primary-button" type="submit" disabled={authSaving}>{authSaving ? 'Patientez…' : registering ? 'Créer mon espace' : 'Se connecter'}</button><button type="button" className="text-button" onClick={() => navigate(registering ? '/login' : '/register')}>{registering ? 'Déjà inscrit ? Connexion' : 'Créer un compte'}</button></div></form></section></div></section></main>
  }

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="/" onClick={event => { event.preventDefault(); navigate('/') }}><span className="brand-mark">W</span> Workspan</a>
        <p className="nav-label">ESPACE DE TRAVAIL</p>
        <a className={`nav-item ${isOrganizationsPage ? 'active' : ''}`} href="/organizations" onClick={event => { event.preventDefault(); navigate('/organizations') }}><span>◫</span> Organisations</a>
        <div className="sidebar-bottom"><div className="avatar">W</div><div><strong>Workspan</strong><small>Gestion de projets</small></div></div>
      </aside>

      <section className="main-panel">
        <header className="topbar"><span>Workspan <b>/</b> {pageTitle}</span><span className="connection">{authUser?.displayName} · <button className="text-button" onClick={() => void logout()}>Déconnexion</button></span></header>
        <div className="content">
          {isOrganizationsPage && <>
            <div className="page-heading"><div><p className="eyebrow">{today}</p><h1>Bonjour, {authUser?.displayName.split(' ')[0]} ! <span>✳</span></h1><p className="subtitle">Bienvenue dans l’espace de travail de {authUser?.organizationName}.</p></div></div>

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
