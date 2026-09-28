import { useEffect, useState, type FormEvent } from 'react'
import './App.css'

type Organization = {
  id: string
  name: string
  spaces: number
  projects: number
  tasks: number
  createdAt: string
}

function App() {
  const [organizations, setOrganizations] = useState<Organization[]>([])
  const [name, setName] = useState('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  async function loadOrganizations() {
    try {
      const response = await fetch('/api/organizations')
      if (!response.ok) throw new Error('Could not connect to the API.')
      setOrganizations(await response.json() as Organization[])
      setError('')
    } catch {
      setError('The API is unavailable. Start the app with Docker Compose and try again.')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => { void loadOrganizations() }, [])

  async function createOrganization(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!name.trim()) return
    try {
      const response = await fetch('/api/organizations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name }),
      })
      if (!response.ok) {
        const body = await response.json().catch(() => ({})) as { detail?: string; message?: string }
        throw new Error(body.detail ?? body.message ?? 'Could not create organization.')
      }
      setName('')
      await loadOrganizations()
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'Could not create organization.')
    }
  }

  const totals = organizations.reduce((sum, item) => ({
    spaces: sum.spaces + item.spaces,
    projects: sum.projects + item.projects,
    tasks: sum.tasks + item.tasks,
  }), { spaces: 0, projects: 0, tasks: 0 })
  const today = new Intl.DateTimeFormat('en-US', { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date()).toUpperCase()

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#home"><span className="brand-mark">i</span> ilot</a>
        <p className="nav-label">WORKSPACE</p>
        <a className="nav-item active" href="#home"><span>▦</span> Overview</a>
        <a className="nav-item" href="#organizations"><span>◫</span> Organizations</a>
        <div className="sidebar-bottom"><div className="avatar">V</div><div><strong>Workspace</strong><small>Project management</small></div><span className="dots">···</span></div>
      </aside>

      <section className="main-panel" id="home">
        <header className="topbar"><span>Workspace <b>/</b> Overview</span><div className="top-actions"><button className="icon-button" aria-label="Notifications">♧</button><div className="user-avatar">V</div></div></header>
        <div className="content">
          <div className="page-heading"><div><p className="eyebrow">{today}</p><h1>Good morning <span>✳</span></h1><p className="subtitle">Here’s what’s happening across your workspace.</p></div><button className="primary-button" onClick={() => document.getElementById('organization-name')?.focus()}>＋ <span>New organization</span></button></div>

          {error && <div className="alert" role="status">{error}</div>}

          <section className="stats-grid" aria-label="Workspace summary">
            <article className="stat-card"><div className="stat-icon violet">◫</div><p>Organizations</p><strong>{organizations.length}</strong><small>Across your workspace</small></article>
            <article className="stat-card"><div className="stat-icon blue">▦</div><p>Spaces</p><strong>{totals.spaces}</strong><small>Organized for your teams</small></article>
            <article className="stat-card"><div className="stat-icon green">◈</div><p>Active projects</p><strong>{totals.projects}</strong><small>Moving work forward</small></article>
            <article className="stat-card"><div className="stat-icon orange">☑</div><p>Tasks</p><strong>{totals.tasks}</strong><small>Across all projects</small></article>
          </section>

          <section className="organizations-panel" id="organizations">
            <div className="section-heading"><div><h2>Your organizations</h2><p>Manage the teams and projects you work with.</p></div><span className="count-pill">{organizations.length} total</span></div>
            <form className="create-form" onSubmit={createOrganization}><label htmlFor="organization-name">Create an organization</label><div className="input-row"><input id="organization-name" value={name} onChange={event => setName(event.target.value)} placeholder="e.g. Acme Studio" maxLength={50} /><button className="primary-button" type="submit">Create organization</button></div></form>
            {loading ? <div className="empty-state">Loading your workspace…</div> : organizations.length === 0 ? <div className="empty-state"><div className="empty-icon">◫</div><strong>Your workspace is ready</strong><p>Create your first organization to start setting up spaces and projects.</p></div> : <div className="org-list">{organizations.map(org => <article className="org-row" key={org.id}><div className="org-mark">{org.name.charAt(0).toUpperCase()}</div><div className="org-name"><strong>{org.name}</strong><small>Created {new Date(org.createdAt).toLocaleDateString()}</small></div><div className="org-metric"><strong>{org.spaces}</strong><small>Spaces</small></div><div className="org-metric"><strong>{org.projects}</strong><small>Projects</small></div><div className="org-metric"><strong>{org.tasks}</strong><small>Tasks</small></div><button className="icon-button" aria-label={`Open ${org.name}`}>↗</button></article>)}</div>}
          </section>
          <footer>© 2026 ilot <span>·</span> Built for teams that move work forward.</footer>
        </div>
      </section>
    </main>
  )
}

export default App
