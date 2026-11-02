import { useEffect, useMemo, useState } from 'react'
import { employeeService } from '../../api/employeeService'
import { projectResourceService } from '../../api/projectResourceService'
import { projectService } from '../../api/projectService'
import { resourceService } from '../../api/resourceService'
import { spaceService } from '../../api/spaceService'
import { taskService } from '../../api/taskService'
import type { Employee, Project, ProjectResourceAllocation, Resource, Space, Task, TaskStatus } from '../../api/types'
import './analytics.css'

type AnalyticsData = { spaces: Space[]; projects: Project[]; tasks: Task[]; employees: Employee[]; employeesAccessible: boolean; pools: Resource[]; poolsAccessible: boolean; allocations: { project: Project; items: ProjectResourceAllocation[] }[] }
type BarItem = { label: string; value: number }
const number = (value: number) => value.toLocaleString('fr-FR', { maximumFractionDigits: 2 })
const taskLabels: Record<TaskStatus, string> = { TODO: 'À faire', STARTED: 'En cours', BLOCKED: 'Bloquées', DONE: 'Terminées' }

function BarChart({ title, items, format = number }: { title: string; items: BarItem[]; format?: (value: number) => string }) {
  const max = Math.max(1, ...items.map(item => item.value))
  return <section className="analytics-card"><h3>{title}</h3>{items.length ? <div className="analytics-bars">{items.map(item => <div className="analytics-bar-row" key={item.label}><div className="analytics-bar-label" title={item.label}>{item.label}</div><div className="analytics-bar-track"><span style={{ width: `${Math.max(item.value > 0 ? 2 : 0, item.value / max * 100)}%` }} /></div><strong>{format(item.value)}</strong></div>)}</div> : <p className="analytics-empty">Aucune donnée disponible.</p>}</section>
}

export function AnalyticsManager({ canViewEmployees, canViewResourcePools }: { canViewEmployees: boolean; canViewResourcePools: boolean }) {
  const [data, setData] = useState<AnalyticsData | null>(null)
  const [loading, setLoading] = useState(true)
  const [partial, setPartial] = useState(false)
  const [error, setError] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    let active = true
    async function load() {
      setLoading(true); setError(''); setPartial(false)
      try {
        const [spaces, projects, tasks, employees] = await Promise.all([
          spaceService.list(), projectService.list(), taskService.list(), canViewEmployees ? employeeService.list() : Promise.resolve([]),
        ])
        let incomplete = false
        const pools = canViewResourcePools ? (await Promise.all(spaces.map(space => resourceService.listForSpace(space.id).catch(() => { incomplete = true; return [] })))).flat() : []
        const allocations = await Promise.all(projects.map(async project => ({
          project,
          items: await projectResourceService.assigned(project.id).catch(() => { incomplete = true; return [] }),
        })))
        if (active) { setData({ spaces, projects, tasks, employees, employeesAccessible: canViewEmployees, pools, poolsAccessible: canViewResourcePools, allocations }); setPartial(incomplete) }
      } catch {
        if (active) setError('Impossible de charger les données accessibles pour les analyses.')
      } finally { if (active) setLoading(false) }
    }
    void load()
    return () => { active = false }
  }, [refreshKey, canViewEmployees, canViewResourcePools])

  const metrics = useMemo(() => {
    if (!data) return null
    const today = new Date().toISOString().slice(0, 10)
    const projectState = (project: Project) => project.startDate && today < project.startDate ? 'À venir' : project.endDate && today > project.endDate ? 'Terminés' : 'En cours'
    const projectStates = ['En cours', 'À venir', 'Terminés'].map(label => ({ label, value: data.projects.filter(project => projectState(project) === label).length }))
    const taskStatuses = (['TODO', 'STARTED', 'BLOCKED', 'DONE'] as TaskStatus[]).map(status => ({ label: taskLabels[status], value: data.tasks.filter(task => task.status === status).length }))
    const employeeCounts = data.spaces.map(space => ({ label: space.name, value: data.employees.filter(employee => employee.spaceId === space.id).length })).sort((a, b) => b.value - a.value)
    const resourceCounts = [
      { label: 'Employés', value: data.pools.filter(resource => resource.type === 'HUMAN').length },
      { label: 'Équipements', value: data.pools.filter(resource => resource.type === 'EQUIPMENT').reduce((sum, resource) => sum + (resource.quantity ?? 0), 0) },
      { label: 'Budgets', value: data.pools.filter(resource => resource.type === 'BUDGET').length },
    ]
    const committedBySpace = new Map<string, Map<string, number>>()
    const committedByProject = new Map<string, Map<string, number>>()
    for (const { project, items } of data.allocations) for (const item of items) if (item.type === 'BUDGET') {
      const currency = item.currency ?? 'Sans devise'
      const bySpace = committedBySpace.get(project.spaceId) ?? new Map<string, number>()
      bySpace.set(currency, (bySpace.get(currency) ?? 0) + (item.amount ?? 0)); committedBySpace.set(project.spaceId, bySpace)
      const byProject = committedByProject.get(project.id) ?? new Map<string, number>()
      byProject.set(currency, (byProject.get(currency) ?? 0) + (item.amount ?? 0)); committedByProject.set(project.id, byProject)
    }
    const budgetCurrencies = [...new Set([...committedBySpace.values(), ...committedByProject.values()].flatMap(currencyMap => [...currencyMap.keys()]))].sort()
    const spaceBudgets = budgetCurrencies.map(currency => ({ currency, items: data.spaces.map(space => ({ label: space.name, value: committedBySpace.get(space.id)?.get(currency) ?? 0 })).filter(item => item.value > 0).sort((a, b) => b.value - a.value) }))
    const projectBudgets = budgetCurrencies.map(currency => ({ currency, items: data.projects.map(project => ({ label: project.name, value: committedByProject.get(project.id)?.get(currency) ?? 0 })).filter(item => item.value > 0).sort((a, b) => b.value - a.value) }))
    const spaceProjects = data.spaces.map(space => ({ label: space.name, value: data.projects.filter(project => project.spaceId === space.id).length })).sort((a, b) => b.value - a.value)
    const activeEmployees = data.employees.filter(employee => employee.status.toUpperCase() === 'AVAILABLE').length
    const availableBySpace = budgetCurrencies.map(currency => ({ currency, items: data.spaces.map(space => ({
      label: space.name,
      value: data.pools.filter(resource => resource.spaceId === space.id && resource.type === 'BUDGET' && (resource.currency ?? 'Sans devise') === currency).reduce((sum, resource) => sum + (resource.availableAmount ?? 0), 0),
    })).filter(item => item.value > 0).sort((a, b) => b.value - a.value) }))
    return { projectStates, taskStatuses, employeeCounts, resourceCounts, spaceBudgets, projectBudgets, spaceProjects, budgetCurrencies, availableBySpace, activeEmployees }
  }, [data])

  if (loading) return <div className="empty-state">Chargement des analyses…</div>
  if (error) return <div className="alert" role="alert">{error}</div>
  if (!data || !metrics) return null

  return <div className="analytics-dashboard">
    <div className="analytics-heading"><div><p className="eyebrow">VUE D’ENSEMBLE</p><h2>Analyses de l’espace de travail</h2><p>Indicateurs calculés à partir des éléments auxquels vous avez accès.</p></div><button type="button" className="secondary-button" onClick={() => setRefreshKey(current => current + 1)}>Actualiser</button></div>
    {partial && <p className="analytics-notice">Certains pools ou affectations de ressources ne sont pas accessibles avec vos permissions; les graphiques correspondants peuvent être incomplets.</p>}
    <div className="analytics-kpis"><article><span>Employés visibles</span><strong>{data.employeesAccessible ? number(data.employees.length) : '—'}</strong><small>{data.employeesAccessible ? `${number(metrics.activeEmployees)} actifs` : 'Permission requise'}</small></article><article><span>Espaces accessibles</span><strong>{number(data.spaces.length)}</strong><small>{number(data.projects.length)} projets</small></article><article><span>Tâches</span><strong>{number(data.tasks.length)}</strong><small>{number(data.tasks.filter(task => task.status === 'DONE').length)} terminées</small></article><article><span>Ressources du pool</span><strong>{data.poolsAccessible ? partial ? `${number(data.pools.length)}+` : number(data.pools.length) : '—'}</strong><small>{data.poolsAccessible ? 'Dans les espaces accessibles' : 'Permission requise'}</small></article></div>
    <div className="analytics-grid">
      {data.employeesAccessible ? <BarChart title="Effectif par espace" items={metrics.employeeCounts} format={value => `${number(value)} pers.`} /> : <section className="analytics-card"><h3>Effectif par espace</h3><p className="analytics-empty">La permission « Voir les employés » est requise pour ces données.</p></section>}
      <BarChart title="Projets par espace" items={metrics.spaceProjects} />
      <BarChart title="État des projets" items={metrics.projectStates} />
      <BarChart title="État des tâches" items={metrics.taskStatuses} />
      {data.poolsAccessible ? <BarChart title="Ressources dans les pools" items={metrics.resourceCounts} /> : <section className="analytics-card"><h3>Ressources dans les pools</h3><p className="analytics-empty">La permission « Voir le pool de ressources » est requise pour ces données.</p></section>}
      {metrics.budgetCurrencies.map(currency => <BarChart key={`space-${currency}`} title={`Budget engagé par espace · ${currency}`} items={metrics.spaceBudgets.find(item => item.currency === currency)?.items ?? []} format={value => `${number(value)} ${currency}`} />)}
      {metrics.budgetCurrencies.map(currency => <BarChart key={`project-${currency}`} title={`Budget engagé par projet · ${currency}`} items={metrics.projectBudgets.find(item => item.currency === currency)?.items ?? []} format={value => `${number(value)} ${currency}`} />)}
      {data.poolsAccessible && metrics.budgetCurrencies.map(currency => <BarChart key={`available-${currency}`} title={`Budget libre par espace · ${currency}`} items={metrics.availableBySpace.find(item => item.currency === currency)?.items ?? []} format={value => `${number(value)} ${currency}`} />)}
    </div>
    <p className="analytics-footnote">Le budget engagé correspond aux montants affectés aux projets. Les dépenses réelles ne sont pas enregistrées dans Workspan.</p>
  </div>
}
