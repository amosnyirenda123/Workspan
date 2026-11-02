import type { FormEvent } from 'react'

export type OrganizationRegistrationValues = {
  organizationName: string
  description: string
  website: string
  contactEmail: string
  phone: string
  displayName: string
  email: string
  password: string
}

type RegistrationField = keyof OrganizationRegistrationValues

type OrganizationRegistrationFormProps = {
  values: OrganizationRegistrationValues
  onChange: (field: RegistrationField, value: string) => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
  error: string
  saving: boolean
  onLogin: () => void
}

export function OrganizationRegistrationForm({ values, onChange, onSubmit, error, saving, onLogin }: OrganizationRegistrationFormProps) {
  return <main className="app-shell"><section className="main-panel" style={{ maxWidth: 720, margin: '6vh auto', borderRadius: 18 }}><div className="content">
    <a className="brand" href="/" onClick={event => event.preventDefault()}><span className="brand-mark">W</span> Workspan</a>
    <div className="page-heading compact-heading"><div><p className="eyebrow">ESPACE DE TRAVAIL</p><h1>Créez votre espace</h1><p className="subtitle">Votre organisation et votre compte administrateur seront créés ensemble.</p></div></div>
    <section className="form-panel"><form className="organization-form" onSubmit={onSubmit}>
      <div className="form-grid">
        <div className="form-field"><label htmlFor="auth-org">Nom de l’organisation <span>*</span></label><input id="auth-org" required maxLength={50} value={values.organizationName} onChange={event => onChange('organizationName', event.target.value)} /></div>
        <div className="form-field"><label htmlFor="auth-website">Site web</label><input id="auth-website" type="url" maxLength={255} value={values.website} onChange={event => onChange('website', event.target.value)} /></div>
        <div className="form-field full-width"><label htmlFor="auth-description">Description</label><textarea id="auth-description" maxLength={500} value={values.description} onChange={event => onChange('description', event.target.value)} /></div>
        <div className="form-field"><label htmlFor="auth-contact-email">E-mail de contact</label><input id="auth-contact-email" type="email" maxLength={254} value={values.contactEmail} onChange={event => onChange('contactEmail', event.target.value)} /></div>
        <div className="form-field"><label htmlFor="auth-phone">Téléphone</label><input id="auth-phone" type="tel" maxLength={30} value={values.phone} onChange={event => onChange('phone', event.target.value)} /></div>
        <div className="form-field"><label htmlFor="auth-name">Votre nom <span>*</span></label><input id="auth-name" required maxLength={100} value={values.displayName} onChange={event => onChange('displayName', event.target.value)} /></div>
        <div className="form-field"><label htmlFor="auth-email">E-mail du compte <span>*</span></label><input id="auth-email" type="email" required maxLength={254} autoComplete="email" value={values.email} onChange={event => onChange('email', event.target.value)} /></div>
        <div className="form-field full-width"><label htmlFor="auth-password">Mot de passe <span>*</span></label><input id="auth-password" type="password" required minLength={10} maxLength={128} autoComplete="new-password" value={values.password} onChange={event => onChange('password', event.target.value)} /><small className="field-hint">10 caractères minimum.</small></div>
      </div>
      {error && <div className="alert form-alert" role="alert">{error}</div>}
      <div className="form-actions"><button className="primary-button" type="submit" disabled={saving}>{saving ? 'Patientez…' : 'Créer mon espace'}</button><button type="button" className="text-button" onClick={onLogin}>Déjà inscrit ? Connexion</button></div>
    </form></section>
  </div></section></main>
}