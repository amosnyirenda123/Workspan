import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { accessService } from '../../api/accessService'
import { getApiErrorMessage } from '../../api/http'
import type { AccessManagementContext, AccessMember, AccessRole, PermissionDefinition, RoleAssignment, RoleScope } from '../../api/types'
import './permission-picker.css'

const isBuiltInRole = (name: string) => ['ADMIN', 'EMPLOYEE'].includes(name) || name.startsWith('SPACE_ADMIN - ') || name.startsWith('PROJECT_ADMIN - ')

export function AccessManager() {
  const [context, setContext] = useState<AccessManagementContext | null>(null)
  const [scope, setScope] = useState<RoleScope>('ORGANIZATION')
  const [contextId, setContextId] = useState('')
  const [permissions, setPermissions] = useState<PermissionDefinition[]>([])
  const [members, setMembers] = useState<AccessMember[]>([])
  const [roles, setRoles] = useState<AccessRole[]>([])
  const [assignments, setAssignments] = useState<RoleAssignment[]>([])
  const [roleName, setRoleName] = useState('')
  const [selectedPermissions, setSelectedPermissions] = useState<string[]>([])
  const [permissionActionFilter, setPermissionActionFilter] = useState('ALL')
  const [permissionCategoryFilter, setPermissionCategoryFilter] = useState('ALL')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [memberId, setMemberId] = useState('')
  const [roleId, setRoleId] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    accessService.managementContext().then(data => {
      if (!active) return
      setContext(data)
      const initialScope: RoleScope = data.canManageOrganization ? 'ORGANIZATION' : data.spaces.length ? 'SPACE' : 'PROJECT'
      setScope(initialScope)
      setContextId(initialScope === 'ORGANIZATION' ? data.organizationId : initialScope === 'SPACE' ? data.spaces[0]?.id ?? '' : data.projects[0]?.id ?? '')
      return accessService.permissions().then(setPermissions)
    }).catch(cause => { if (active) setError(getApiErrorMessage(cause, 'Impossible de charger les droits de gestion.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [])

  const options = useMemo(() => scope === 'SPACE' ? context?.spaces ?? [] : scope === 'PROJECT' ? context?.projects ?? [] : [], [context, scope])
  const visiblePermissions = permissions.filter(permission => scope === 'ORGANIZATION'
    || (scope === 'SPACE' ? !['organization.view', 'space.create'].includes(permission.key)
      : ['access.manage', 'analytics.view', 'project.view', 'project.edit', 'project.delete', 'project.resources.manage', 'task.view', 'task.create', 'task.edit', 'task.delete'].includes(permission.key)))
  const permissionActions = [...new Set(visiblePermissions.map(permission => permission.action))]
  const permissionCategories = [...new Set(visiblePermissions.map(permission => permission.category))]
  const filteredPermissions = visiblePermissions.filter(permission => (permissionActionFilter === 'ALL' || permission.action === permissionActionFilter)
    && (permissionCategoryFilter === 'ALL' || permission.category === permissionCategoryFilter))
  const permissionGroups = filteredPermissions.reduce((groups, permission) => {
    const group = groups.find(item => item.category === permission.category)
    if (group) group.permissions.push(permission)
    else groups.push({ category: permission.category, permissions: [permission] })
    return groups
  }, [] as { category: string; permissions: PermissionDefinition[] }[])

  async function refresh() {
    if (!contextId) return
    const [roleData, assignmentData, memberData] = await Promise.all([
      accessService.roles(scope, contextId), accessService.assignments(scope, contextId), accessService.members(scope, contextId),
    ])
    setRoles(roleData); setAssignments(assignmentData); setMembers(memberData)
    setRoleId(current => roleData.some(role => role.id === current) ? current : roleData[0]?.id ?? '')
    setMemberId(current => memberData.some(member => member.userId === current) ? current : memberData[0]?.userId ?? '')
  }

  useEffect(() => {
    if (!contextId) return
    let active = true
    setLoading(true); setError(''); setEditingId(null); setRoleName(''); setSelectedPermissions([])
    Promise.all([accessService.roles(scope, contextId), accessService.assignments(scope, contextId), accessService.members(scope, contextId)])
      .then(([roleData, assignmentData, memberData]) => {
        if (!active) return
        setRoles(roleData); setAssignments(assignmentData); setMembers(memberData)
        setRoleId(roleData[0]?.id ?? ''); setMemberId(memberData[0]?.userId ?? '')
      }).catch(cause => { if (active) setError(getApiErrorMessage(cause, 'Impossible de charger les rôles de ce périmètre.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [scope, contextId])

  function changeScope(next: RoleScope) {
    setScope(next)
    setContextId(next === 'ORGANIZATION' ? context?.organizationId ?? '' : next === 'SPACE' ? context?.spaces[0]?.id ?? '' : context?.projects[0]?.id ?? '')
  }

  function editRole(role: AccessRole) { setEditingId(role.id); setRoleName(role.name); setSelectedPermissions(role.permissionKeys); setError('') }
  function resetForm() { setEditingId(null); setRoleName(''); setSelectedPermissions([]) }

  async function saveRole(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!contextId) return
    setSaving(true); setError('')
    try {
      const payload = { name: roleName.trim(), scope, contextId, permissionKeys: selectedPermissions }
      if (editingId) await accessService.updateRole(editingId, payload)
      else await accessService.createRole(payload)
      resetForm(); await refresh()
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’enregistrer ce rôle.')) }
    finally { setSaving(false) }
  }

  async function deleteRole(role: AccessRole) {
    if (!window.confirm(`Supprimer le rôle « ${role.name} » ?`)) return
    setSaving(true); setError('')
    try { await accessService.removeRole(role.id, scope, contextId); resetForm(); await refresh() }
    catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de supprimer ce rôle.')) }
    finally { setSaving(false) }
  }

  async function assign(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); if (!memberId || !roleId) return
    setSaving(true); setError('')
    try { await accessService.assign(scope, contextId, memberId, roleId); await refresh() }
    catch (cause) { setError(getApiErrorMessage(cause, 'Impossible d’attribuer ce rôle.')) }
    finally { setSaving(false) }
  }

  async function unassign(item: RoleAssignment) {
    setSaving(true); setError('')
    try { await accessService.unassign(scope, contextId, item.userId, item.roleId); await refresh() }
    catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de retirer ce rôle.')) }
    finally { setSaving(false) }
  }

  if (loading && !context) return <div className="empty-state">Chargement des droits d’accès…</div>
  if (!context) return <div className="alert" role="alert">{error || 'Aucun périmètre de gestion disponible.'}</div>
  const canManageScope = context.canManageOrganization || context.spaces.length > 0 || context.projects.length > 0
  if (!canManageScope) return null

  return <div className="access-manager">
    <div className="crud-heading"><div><p className="eyebrow">ADMINISTRATION</p><h2>Rôles et permissions</h2><p>Un rôle regroupe des droits. Les attributions héritent des périmètres parents.</p></div></div>
    <section className="form-panel access-scope-panel"><div className="form-grid">
      <div className="form-field"><label htmlFor="access-scope">Périmètre</label><select id="access-scope" value={scope} onChange={event => changeScope(event.target.value as RoleScope)}><option value="ORGANIZATION" disabled={!context.canManageOrganization}>Organisation</option><option value="SPACE" disabled={!context.spaces.length}>Espace</option><option value="PROJECT" disabled={!context.projects.length}>Projet</option></select></div>
      {scope !== 'ORGANIZATION' && <div className="form-field"><label htmlFor="access-context">{scope === 'SPACE' ? 'Espace' : 'Projet'}</label><select id="access-context" value={contextId} onChange={event => setContextId(event.target.value)}>{options.map(option => <option key={option.id} value={option.id}>{option.parentName ? `${option.parentName} · ` : ''}{option.name}</option>)}</select></div>}
    </div></section>
    {error && <div className="alert form-alert" role="alert">{error}</div>}
    {loading ? <div className="empty-state">Chargement des rôles…</div> : <div className="access-layout">
      <section className="form-panel crud-form-panel">
        <div className="form-panel-heading"><div className="form-icon">R</div><div><h2>{editingId ? 'Modifier le rôle' : 'Créer un rôle'}</h2><p>Choisissez les permissions utilisables à ce périmètre.</p></div></div>
        <form className="organization-form" onSubmit={saveRole}><div className="form-field"><label htmlFor="role-name">Nom du rôle <span>*</span></label><input id="role-name" required maxLength={60} value={roleName} onChange={event => setRoleName(event.target.value)} /></div>
          <fieldset className="permission-picker"><legend>Permissions · {selectedPermissions.length} sélectionnée(s)</legend>
            <div className="permission-filters"><div className="form-field"><label htmlFor="permission-action-filter">Action</label><select id="permission-action-filter" value={permissionActionFilter} onChange={event => setPermissionActionFilter(event.target.value)}><option value="ALL">Toutes les actions</option>{permissionActions.map(action => <option key={action} value={action}>{action}</option>)}</select></div><div className="form-field"><label htmlFor="permission-category-filter">Fonctionnalité</label><select id="permission-category-filter" value={permissionCategoryFilter} onChange={event => setPermissionCategoryFilter(event.target.value)}><option value="ALL">Toutes les fonctionnalités</option>{permissionCategories.map(category => <option key={category} value={category}>{category}</option>)}</select></div></div>
            {permissionGroups.length ? <div className="permission-groups">{permissionGroups.map(group => <section className="permission-group" key={group.category}><h3>{group.category}<span>{group.permissions.length}</span></h3>{group.permissions.map(permission => <label key={permission.key} className="permission-option"><input type="checkbox" checked={selectedPermissions.includes(permission.key)} onChange={event => setSelectedPermissions(current => event.target.checked ? [...current, permission.key] : current.filter(key => key !== permission.key))} /><span><strong>{permission.label}</strong><small>{permission.description}</small></span><small className="permission-action">{permission.action}</small></label>)}</section>)}</div> : <p className="field-hint">Aucune permission ne correspond à ces filtres.</p>}
          </fieldset>
          <div className="form-actions"><button className="primary-button" disabled={saving || !contextId}>{saving ? 'Enregistrement…' : editingId ? 'Enregistrer le rôle' : 'Créer le rôle'}</button>{editingId && <button type="button" className="secondary-button" onClick={resetForm}>Annuler</button>}</div>
        </form>
      </section>
      <section className="access-right-column">
        <div className="crud-list-panel"><div className="section-heading"><div><h2>Rôles disponibles</h2><p>{scope === 'ORGANIZATION' ? 'Ces rôles s’appliquent dans toute l’organisation.' : 'Ces rôles peuvent être attribués dans le périmètre sélectionné.'}</p></div><span className="count-pill">{roles.length}</span></div>
          {roles.length ? roles.map(role => <article className="crud-row" key={role.id}><div className="crud-symbol violet">R</div><div className="crud-row-main"><strong>{role.name}</strong><small>{role.permissionKeys.length} permission(s)</small></div><div className="crud-actions"><button type="button" className="secondary-button" disabled={saving || isBuiltInRole(role.name)} onClick={() => editRole(role)}>Modifier</button>{!isBuiltInRole(role.name) && <button type="button" className="danger-button" disabled={saving} onClick={() => void deleteRole(role)}>Supprimer</button>}</div></article>) : <div className="empty-state">Aucun rôle dans ce périmètre.</div>}
        </div>
        <div className="crud-list-panel access-assignment-panel"><div className="section-heading"><div><h2>Attribuer un rôle</h2><p>Seuls les comptes actifs de l’organisation sont listés.</p></div></div>
          <form className="access-assignment-form" onSubmit={assign}><div className="form-field"><label htmlFor="access-member">Membre</label><select id="access-member" value={memberId} onChange={event => setMemberId(event.target.value)}><option value="">Choisir un membre</option>{members.map(member => <option key={member.userId} value={member.userId}>{member.displayName} · {member.email}</option>)}</select></div><div className="form-field"><label htmlFor="access-role">Rôle</label><select id="access-role" value={roleId} onChange={event => setRoleId(event.target.value)}><option value="">Choisir un rôle</option>{roles.map(role => <option key={role.id} value={role.id}>{role.name}</option>)}</select></div><button className="primary-button" disabled={saving || !memberId || !roleId}>{saving ? 'Enregistrement…' : 'Attribuer'}</button></form>
          {assignments.map(item => <article className="crud-row" key={`${item.userId}-${item.roleId}`}><div className="crud-symbol green">{item.displayName.charAt(0).toUpperCase()}</div><div className="crud-row-main"><strong>{item.displayName} · {item.roleName}</strong><small>{item.email}</small></div><div className="crud-actions"><button type="button" className="danger-button" disabled={saving || item.roleName === 'ADMIN'} onClick={() => void unassign(item)}>Retirer</button></div></article>)}
        </div>
      </section>
    </div>}
  </div>
}
