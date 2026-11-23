import { useState, type FormEvent } from 'react'
import { authService } from '../../api/authService'
import { getApiErrorMessage } from '../../api/http'

export function PasswordResetPage() {
  const token = new URLSearchParams(window.location.search).get('token') ?? ''
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      if (token) {
        if (password !== confirm) { setError('Les mots de passe ne correspondent pas.'); return }
        await authService.completePasswordReset(token, password)
      } else await authService.requestPasswordReset(email)
      setDone(true)
    } catch (cause) { setError(getApiErrorMessage(cause, 'Impossible de traiter la demande.')) }
    finally { setBusy(false) }
  }

  return <main className="app-shell"><section className="main-panel" style={{ maxWidth: 560, margin: '8vh auto', borderRadius: 18 }}><div className="content"><a className="brand" href="/" onClick={event => { event.preventDefault(); window.location.assign('/') }}><span className="brand-mark">W</span> Workspan</a><div className="page-heading compact-heading"><div><p className="eyebrow">SÉCURITÉ DU COMPTE</p><h1>{token ? 'Choisir un nouveau mot de passe' : 'Réinitialiser le mot de passe'}</h1><p className="subtitle">{token ? 'Choisissez un mot de passe de 12 caractères minimum.' : 'Saisissez votre e-mail. Si un compte existe, un lien de réinitialisation vous sera envoyé.'}</p></div></div>{done ? <section className="form-panel"><p role="status">{token ? 'Mot de passe mis à jour. Vous pouvez maintenant vous connecter.' : 'Si un compte actif correspond à cet e-mail, un lien vous a été envoyé. Vérifiez votre boîte de réception.'}</p><button className="primary-button" onClick={() => window.location.assign('/login')}>Retour à la connexion</button></section> : <section className="form-panel"><form className="organization-form" onSubmit={submit}><div className="form-grid">{token ? <><div className="form-field full-width"><label htmlFor="reset-password">Nouveau mot de passe</label><input id="reset-password" required type="password" minLength={12} maxLength={128} autoComplete="new-password" value={password} onChange={event => setPassword(event.target.value)} /></div><div className="form-field full-width"><label htmlFor="reset-confirm">Confirmer le mot de passe</label><input id="reset-confirm" required type="password" minLength={12} maxLength={128} autoComplete="new-password" value={confirm} onChange={event => setConfirm(event.target.value)} /></div></> : <div className="form-field full-width"><label htmlFor="reset-email">E-mail</label><input id="reset-email" required type="email" maxLength={254} autoComplete="email" value={email} onChange={event => setEmail(event.target.value)} /></div>}</div>{error && <div className="alert form-alert" role="alert">{error}</div>}<div className="form-actions"><button className="primary-button" disabled={busy}>{busy ? 'Patientez…' : token ? 'Enregistrer le mot de passe' : 'Envoyer le lien'}</button><button type="button" className="text-button" onClick={() => window.location.assign('/login')}>Retour à la connexion</button></div></form></section>}</div></section></main>
}
