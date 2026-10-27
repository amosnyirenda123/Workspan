import { useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import { employeeService } from '../../api/employeeService'
import { getApiErrorMessage } from '../../api/http'
import { spaceService } from '../../api/spaceService'
import type { CredentialBatchResponse, CredentialDeliveryResult, Employee, EmployeeImportRowResult, Space } from '../../api/types'
import { downloadEmployeeTemplate, parseEmployeeFile, type EmployeeImportPreviewRow } from './employeeImport'

type EmployeeForm = { spaceId: string; name: string; email: string; jobTitle: string }
type ImportReportRow = { rowNumber: number; status: string; message: string }
const emptyForm: EmployeeForm = { spaceId: '', name: '', email: '', jobTitle: '' }

export function EmployeesManager({ onChange }: { onChange: () => void }) {
  const [employees, setEmployees] = useState<Employee[]>([])
  const [spaces, setSpaces] = useState<Space[]>([])
  const [form, setForm] = useState<EmployeeForm>(emptyForm)
  const [mode, setMode] = useState<'single' | 'import'>('single')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [importRows, setImportRows] = useState<EmployeeImportPreviewRow[]>([])
  const [importReport, setImportReport] = useState<ImportReportRow[]>([])
  const [importFileName, setImportFileName] = useState('')
  const [importSummary, setImportSummary] = useState('')
  const [importError, setImportError] = useState('')
  const [importBusy, setImportBusy] = useState(false)
  const [credentialBusy, setCredentialBusy] = useState<string | null>(null)
  const [credentialSummary, setCredentialSummary] = useState('')
  const [credentialResults, setCredentialResults] = useState<CredentialDeliveryResult[]>([])

  useEffect(() => {
    const controller = new AbortController()
    Promise.all([employeeService.list({ signal: controller.signal }), spaceService.list({ signal: controller.signal })])
      .then(([employeeData, spaceData]) => { setEmployees(employeeData); setSpaces(spaceData) })
      .catch(cause => { if (!controller.signal.aborted) setError(getApiErrorMessage(cause, 'Impossible de charger les employes et les espaces.')) })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      const employee = await employeeService.create({
        spaceId: form.spaceId,
        name: form.name.trim(),
        email: form.email.trim(),
        jobTitle: form.jobTitle.trim() || null,
      })
      setEmployees(current => [...current, employee].sort((a, b) => a.name.localeCompare(b.name)))
      setForm(emptyForm)
      onChange()
    } catch (cause) {
      setError(getApiErrorMessage(cause, 'Impossible de creer cet employe.'))
    } finally {
      setSaving(false)
    }
  }

  async function selectImportFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) return
    setImportFileName(file.name)
    setImportError('')
    setImportReport([])
    setImportSummary('')
    try {
      const rows = await parseEmployeeFile(file, spaces)
      if (rows.length > 500) throw new Error('Importez au maximum 500 employes a la fois.')
      setImportRows(rows)
      if (!rows.length) setImportError('Aucune ligne employe trouvee dans le fichier.')
    } catch (cause) {
      setImportRows([])
      setImportError(cause instanceof Error ? cause.message : 'Impossible de lire ce fichier.')
    }
  }

  async function submitImport() {
    const validRows = importRows.filter(row => !row.error).map(row => ({
      rowNumber: row.rowNumber,
      spaceId: row.spaceId,
      name: row.name,
      email: row.email,
      jobTitle: row.jobTitle,
    }))
    if (validRows.length === 0) {
      setImportError('Corrigez les lignes en erreur avant de lancer l import.')
      return
    }
    setImportBusy(true)
    setImportError('')
    try {
      const result = await employeeService.import({ employees: validRows })
      const localErrors: ImportReportRow[] = importRows.filter(row => row.error).map(row => ({ rowNumber: row.rowNumber, status: 'Erreur', message: row.error ?? '' }))
      const serverRows: ImportReportRow[] = result.rows.map((row: EmployeeImportRowResult) => ({
        rowNumber: row.rowNumber,
        status: row.status === 'CREATED' ? 'Ajoute' : 'Ignore',
        message: row.message,
      }))
      setImportReport([...localErrors, ...serverRows].sort((a, b) => a.rowNumber - b.rowNumber))
      setImportSummary(`${result.created} employe(s) ajoute(s), ${result.skipped + localErrors.length} ligne(s) ignoree(s).`)
      setEmployees(current => [...current, ...result.rows.flatMap(row => row.employee ? [row.employee] : [])].sort((a, b) => a.name.localeCompare(b.name)))
      onChange()
    } catch (cause) {
      setImportError(getApiErrorMessage(cause, 'Impossible d importer ces employes.'))
    } finally {
      setImportBusy(false)
    }
  }

  async function sendEmployeeCredentials(employee: Employee) {
    if (credentialBusy) return
    let target = employee
    if (!target.email) {
      const email = window.prompt(`Saisissez l'adresse e-mail de ${target.name} :`)
      if (!email?.trim()) return
      try {
        target = await employeeService.updateEmail(target.id, email.trim())
        setEmployees(current => current.map(item => item.id === target.id ? target : item))
      } catch (cause) {
        setCredentialSummary(getApiErrorMessage(cause, `Impossible d'enregistrer l'adresse e-mail de ${target.name}.`))
        return
      }
    }
    if (!target.email) return
    if (target.credentialEmailSentAt && !window.confirm(`Regenerer le mot de passe temporaire et l'envoyer a ${target.email} ? Son mot de passe actuel cessera de fonctionner.`)) return
    setCredentialBusy(employee.id)
    setCredentialSummary('')
    setCredentialResults([])
    try {
      const result = await employeeService.sendCredentials(target.id)
      setCredentialSummary(result.status === 'SENT' ? `Identifiants envoyes a ${result.email}.` : result.message)
      setEmployees(current => current.map(item => item.id === target.id ? { ...item, hasAccount: true, credentialEmailSentAt: result.sentAt } : item))
      onChange()
    } catch (cause) {
      setCredentialSummary(getApiErrorMessage(cause, `Impossible d'envoyer les identifiants a ${target.email}.`))
    } finally {
      setCredentialBusy(null)
    }
  }

  async function sendAllCredentials() {
    if (credentialBusy || !employees.length) return
    const pending = employees.filter(employee => !employee.credentialEmailSentAt).length
    if (pending === 0) {
      setCredentialSummary('Tous les employes ont deja recu leurs identifiants. Utilisez le bouton individuel pour regenerer un mot de passe.')
      return
    }
    if (!window.confirm(`Envoyer les identifiants temporaires aux ${pending} employe(s) qui ne les ont pas encore recus ?`)) return
    setCredentialBusy('all')
    setCredentialSummary('')
    setCredentialResults([])
    try {
      const result: CredentialBatchResponse = await employeeService.sendAllCredentials()
      setCredentialResults(result.results)
      setCredentialSummary(`${result.sent} envoye(s), ${result.skipped} deja traite(s), ${result.failed} echec(s).`)
      const sent = new Map(result.results.filter(row => row.status === 'SENT').map(row => [row.employeeId, row.sentAt]))
      setEmployees(current => current.map(employee => sent.has(employee.id) ? { ...employee, hasAccount: true, credentialEmailSentAt: sent.get(employee.id) ?? null } : employee))
      onChange()
    } catch (cause) {
      setCredentialSummary(getApiErrorMessage(cause, 'Impossible de lancer l envoi des identifiants.'))
    } finally {
      setCredentialBusy(null)
    }
  }

  async function downloadTemplate(format: 'csv' | 'xlsx') {
    setImportError('')
    try {
      await downloadEmployeeTemplate(format)
    } catch {
      setImportError('Impossible de generer le modele.')
    }
  }

  return <>
    <div className="crud-tabs employee-modes" role="tablist" aria-label="Mode de creation des employes">
      <button role="tab" aria-selected={mode === 'single'} className={mode === 'single' ? 'active' : ''} onClick={() => setMode('single')}>Ajouter un employe</button>
      <button role="tab" aria-selected={mode === 'import'} className={mode === 'import' ? 'active' : ''} onClick={() => setMode('import')}>Importer CSV / Excel</button>
    </div>
    <div className="crud-layout">
      {mode === 'single' ? <section className="form-panel crud-form-panel">
        <div className="form-panel-heading"><div className="form-icon">E</div><div><h2>Nouvel employe</h2><p>Ajoutez un membre a un espace de votre organisation.</p></div></div>
        <form className="organization-form" onSubmit={submit}>
          <div className="form-grid">
            <div className="form-field full-width"><label htmlFor="employee-name">Nom complet <span>*</span></label><input id="employee-name" required maxLength={120} autoComplete="name" value={form.name} onChange={event => setForm({ ...form, name: event.target.value })} /></div>
            <div className="form-field full-width"><label htmlFor="employee-email">E-mail <span>*</span></label><input id="employee-email" type="email" required maxLength={254} autoComplete="email" value={form.email} onChange={event => setForm({ ...form, email: event.target.value })} /></div>
            <div className="form-field full-width"><label htmlFor="employee-title">Fonction</label><input id="employee-title" maxLength={100} value={form.jobTitle} onChange={event => setForm({ ...form, jobTitle: event.target.value })} placeholder="Ex. Chef de projet" /></div>
            <div className="form-field full-width"><label htmlFor="employee-space">Espace <span>*</span></label><select id="employee-space" required value={form.spaceId} onChange={event => setForm({ ...form, spaceId: event.target.value })}><option value="">Choisir un espace</option>{spaces.map(space => <option key={space.id} value={space.id}>{space.name}</option>)}</select></div>
          </div>
          {error && <div className="alert form-alert" role="alert">{error}</div>}
          {spaces.length === 0 && <p className="field-hint">Creez d'abord un espace pour y rattacher l'employe.</p>}
          <div className="form-actions"><button className="primary-button" disabled={saving || spaces.length === 0}>{saving ? 'Creation...' : "Creer l'employe"}</button></div>
        </form>
      </section> : <section className="form-panel crud-form-panel employee-import-panel">
        <div className="form-panel-heading"><div className="form-icon">⇧</div><div><h2>Importer des employes</h2><p>Chargez un fichier CSV ou Excel, jusqu'a 500 lignes.</p></div></div>
        <div className="employee-import-help"><p>Le fichier doit contenir le nom, l'e-mail et le nom d'un espace existant. La fonction est facultative. Les adresses e-mail deja utilisees seront ignorees.</p><div className="employee-template-actions"><button type="button" className="secondary-button" onClick={() => void downloadTemplate('csv')}>Telecharger le modele CSV</button><button type="button" className="secondary-button" onClick={() => void downloadTemplate('xlsx')}>Telecharger le modele Excel</button></div></div>
        <div className="form-field employee-file-field"><label htmlFor="employee-file">Fichier CSV ou Excel (.csv, .xlsx, .xls)</label><input id="employee-file" type="file" accept=".csv,.xlsx,.xls" onChange={event => void selectImportFile(event)} /></div>
        {importFileName && <p className="field-hint">Fichier : {importFileName}</p>}
        {importError && <div className="alert form-alert" role="alert">{importError}</div>}
        {importRows.length > 0 && <>
          <p className="field-hint">{importRows.length} ligne(s) lue(s); {importRows.filter(row => row.error).length} erreur(s) a corriger.</p>
          <div className="employee-preview" aria-label="Apercu des lignes importees">
            {importRows.slice(0, 8).map(row => <div className="employee-preview-row" key={row.rowNumber}><span>Ligne {row.rowNumber}</span><strong>{row.name || '(sans nom)'}</strong><span className={row.error ? 'preview-invalid' : 'preview-valid'}>{row.error || row.email}</span></div>)}
            {importRows.length > 8 && <p className="field-hint">Et {importRows.length - 8} autre(s) ligne(s)...</p>}
          </div>
          <div className="form-actions"><button className="primary-button" disabled={importBusy || importReport.length > 0 || !importRows.some(row => !row.error)} onClick={() => void submitImport()}>{importBusy ? 'Import en cours...' : importReport.length ? 'Import termine' : 'Importer les lignes valides'}</button></div>
        </>}
        {importSummary && <div className="import-summary" role="status">{importSummary}</div>}
        {importReport.length > 0 && <div className="employee-import-report" aria-live="polite">{importReport.map(row => <div key={`${row.rowNumber}-${row.status}`}><strong>Ligne {row.rowNumber} - {row.status}</strong><span>{row.message}</span></div>)}</div>}
      </section>}
      <section className="crud-list-panel" aria-live="polite">
        <div className="employee-list-heading"><div><strong>Employes de l'organisation</strong><small>Le premier envoi cree leur compte utilisateur.</small></div><button type="button" className="secondary-button" onClick={() => void sendAllCredentials()} disabled={!!credentialBusy || loading || !employees.length}>{credentialBusy === 'all' ? 'Envoi...' : 'Envoyer a tous'}</button></div>
        {credentialSummary && <div className="credential-summary" role="status">{credentialSummary}</div>}
        {credentialResults.some(row => row.status === 'FAILED') && <div className="credential-failures">{credentialResults.filter(row => row.status === 'FAILED').map(row => <p key={row.employeeId}>{row.email || 'E-mail manquant'}: {row.message}</p>)}</div>}
        {loading ? <div className="empty-state">Chargement des employes...</div> : employees.length ? employees.map(employee => <article className="crud-row employee-row" key={employee.id}><div className="crud-symbol violet">{employee.name.charAt(0).toUpperCase()}</div><div className="crud-row-main"><strong>{employee.name}</strong><small>{employee.email ?? 'E-mail manquant'} - {employee.jobTitle ? `${employee.jobTitle} - ` : ''}{employee.spaceName}</small><small>{employee.credentialEmailSentAt ? `Identifiants envoyes le ${new Date(employee.credentialEmailSentAt).toLocaleDateString('fr-FR')}` : employee.hasAccount ? 'Compte cree, identifiants a envoyer' : 'Aucun compte utilisateur'}</small></div><button type="button" className="secondary-button employee-send-button" onClick={() => void sendEmployeeCredentials(employee)} disabled={!!credentialBusy}>{credentialBusy === employee.id ? 'Envoi...' : !employee.email ? 'Ajouter e-mail et envoyer' : employee.credentialEmailSentAt ? 'Reinitialiser et renvoyer' : 'Envoyer les identifiants'}</button></article>) : <div className="empty-state"><strong>Aucun employe</strong><p>Les employes crees apparaitront ici.</p></div>}
      </section>
    </div>
  </>
}
