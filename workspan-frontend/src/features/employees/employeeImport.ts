import type { EmployeeImportRow, Space } from '../../api/types'

export type EmployeeImportPreviewRow = EmployeeImportRow & { error: string | null }

export async function downloadEmployeeTemplate(format: 'csv' | 'xlsx') {
  const XLSX = await import('xlsx')
  const columns = ['name', 'email', 'jobTitle', 'spaceName']
  if (format === 'csv') {
    const blob = new Blob([`\uFEFF${columns.join(',')}\r\n`], { type: 'text/csv;charset=utf-8' })
    downloadBlob(blob, 'workspan-employees-template.csv')
    return
  }

  const workbook = XLSX.utils.book_new()
  const employeesSheet = XLSX.utils.aoa_to_sheet([columns, ...Array.from({ length: 20 }, () => ['', '', '', ''])])
  employeesSheet['!cols'] = [{ wch: 28 }, { wch: 34 }, { wch: 28 }, { wch: 26 }]
  XLSX.utils.book_append_sheet(workbook, employeesSheet, 'Employees')

  const instructionsSheet = XLSX.utils.aoa_to_sheet([
    ['Employee import template'],
    ['Enter one employee per row on the Employees sheet, starting at row 2. Import the completed workbook in Workspan.'],
    ['Column', 'Required', 'What to enter'],
    ['name', 'Yes', 'Employee full name (maximum 120 characters).'],
    ['email', 'Yes', 'Unique employee email address. It is used to prevent duplicate accounts.'],
    ['jobTitle', 'No', 'Employee job title or function (maximum 100 characters).'],
    ['spaceName', 'Yes', 'Name of an existing Workspan space in your organisation.'],
    [],
    ['Example (for guidance only; do not copy into the Employees sheet)'],
    ['name', 'email', 'jobTitle', 'spaceName'],
    ['Alex Martin', 'alex.martin@example.com', 'Project Manager', 'Marketing'],
  ])
  instructionsSheet['!cols'] = [{ wch: 23 }, { wch: 16 }, { wch: 94 }, { wch: 28 }]
  XLSX.utils.book_append_sheet(workbook, instructionsSheet, 'Instructions')
  const data = XLSX.write(workbook, { bookType: 'xlsx', type: 'array' })
  downloadBlob(new Blob([data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }), 'workspan-employees-template.xlsx')
}

function downloadBlob(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

function normalize(value: unknown) {
  return String(value ?? '').trim().normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]/g, '')
}

export async function parseEmployeeFile(file: File, spaces: Space[]): Promise<EmployeeImportPreviewRow[]> {
  if (file.size > 10 * 1024 * 1024) throw new Error('Le fichier ne peut pas depasser 10 Mo.')
  if (!/\.(csv|xlsx|xls)$/i.test(file.name)) throw new Error('Choisissez un fichier CSV ou Excel (.xlsx, .xls).')

  const XLSX = await import('xlsx')
  const workbook = XLSX.read(await file.arrayBuffer(), { type: 'array', sheetRows: 502 })
  const firstSheet = workbook.Sheets[workbook.SheetNames[0]]
  if (!firstSheet) throw new Error('Le fichier ne contient aucune feuille.')

  const grid = XLSX.utils.sheet_to_json<unknown[]>(firstSheet, { header: 1, defval: '', raw: false })
  if (grid.length < 2) throw new Error('Ajoutez une ligne d en-tetes et au moins un employe.')
  const headers = (grid[0] ?? []).map(normalize)
  const findHeader = (...names: string[]) => headers.findIndex(header => names.includes(header))
  const columns = {
    name: findHeader('name', 'fullname', 'nom', 'nomcomplet'),
    email: findHeader('email', 'mail', 'courriel'),
    jobTitle: findHeader('jobtitle', 'title', 'function', 'fonction', 'poste'),
    spaceName: findHeader('spacename', 'space', 'espace', 'nomespace'),
  }
  const missing = Object.entries(columns).filter(([, index]) => index < 0).map(([name]) => name)
  if (missing.length) throw new Error(`Colonnes requises manquantes : ${missing.join(', ')}. Utilisez le modele CSV fourni.`)

  const spacesByName = new Map<string, Space[]>()
  for (const space of spaces) {
    const key = normalize(space.name)
    spacesByName.set(key, [...(spacesByName.get(key) ?? []), space])
  }

  return grid.slice(1).flatMap((row, index) => {
    const value = (column: number) => String(row[column] ?? '').trim()
    const [name, email, jobTitle, spaceName] = [value(columns.name), value(columns.email), value(columns.jobTitle), value(columns.spaceName)]
    if (![name, email, jobTitle, spaceName].some(Boolean)) return []
    const matchedSpaces = spacesByName.get(normalize(spaceName)) ?? []
    const errors = [
      !name ? 'Le nom est obligatoire.' : name.length > 120 ? 'Le nom depasse 120 caracteres.' : '',
      !email ? 'L adresse e-mail est obligatoire.' : email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? 'Adresse e-mail invalide.' : '',
      jobTitle.length > 100 ? 'La fonction depasse 100 caracteres.' : '',
      !spaceName ? 'L espace est obligatoire.' : matchedSpaces.length === 0 ? `Espace introuvable : ${spaceName}.` : matchedSpaces.length > 1 ? `Le nom d espace est ambigu : ${spaceName}.` : '',
    ].filter(Boolean)
    return [{
      rowNumber: index + 2,
      spaceId: matchedSpaces.length === 1 ? matchedSpaces[0].id : '',
      name,
      email,
      jobTitle: jobTitle || null,
      error: errors.join(' '),
    }]
  })
}
