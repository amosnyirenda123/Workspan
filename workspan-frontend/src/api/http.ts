import axios from 'axios'
import { parseApiError } from './errors'

export const apiClient = axios.create({
  baseURL: '/api',
  withCredentials: true,
  timeout: 15_000,
  headers: { Accept: 'application/json' },
})

export { getApiErrorMessage, hasErrorCode, parseApiError } from './errors'

export type RequestSignal = { signal?: AbortSignal }

// ------------------------------------------------------------------ session expiry

/** Codes meaning "no valid session". INVALID_CREDENTIALS (wrong password on login) is deliberately not one of them. */
const SESSION_EXPIRED_CODES = new Set(['AUTHENTICATION_REQUIRED', 'UNAUTHORIZED'])
/** Endpoints whose 401 is an expected answer, not an expired session: `me` is how the app learns nobody is signed in. */
const AUTH_ENDPOINTS = ['/auth/me', '/auth/login', '/auth/register', '/auth/logout']

type SessionExpiredListener = () => void
const listeners = new Set<SessionExpiredListener>()
let expiryNotified = false

/** Called once when an API call reveals the session has expired (until the user signs in again). Returns an unsubscribe function. */
export function onSessionExpired(listener: SessionExpiredListener): () => void {
  listeners.add(listener)
  return () => { listeners.delete(listener) }
}

function isAuthEndpoint(url: string | undefined): boolean {
  return !!url && AUTH_ENDPOINTS.some(endpoint => url.endsWith(endpoint))
}

apiClient.interceptors.response.use(
  response => {
    // A successful sign-in (or session check) re-arms the notification for the next expiry.
    if (isAuthEndpoint(response.config.url)) expiryNotified = false
    return response
  },
  (error: unknown) => {
    const info = parseApiError(error)
    const url = axios.isAxiosError(error) ? error.config?.url : undefined
    const expired = info.status === 401 && (!info.code || SESSION_EXPIRED_CODES.has(info.code)) && !isAuthEndpoint(url)
    if (expired && !expiryNotified) {
      // Several requests usually fail together; notify only once.
      expiryNotified = true
      listeners.forEach(listener => listener())
    }
    return Promise.reject(error)
  },
)
