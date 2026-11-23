import axios from 'axios'

/**
 * Turns API errors into French messages for the UI.
 *
 * The backend answers every failure with an RFC 7807 problem body carrying a stable `code`
 * (see workspan-backend/.../exception/ErrorCode.java), optional `params` for the message placeholders and,
 * for validation failures, a list of field `errors`. The backend's `detail` is English and meant for logs,
 * so it is never shown: each code is translated here. Keep this catalogue in sync with ErrorCode.java.
 */

export type FieldViolation = { field: string; constraint: string; message?: string; attributes?: Record<string, unknown> }
export type ErrorParams = Record<string, unknown>

export type ApiProblem = {
  type?: string
  title?: string
  status?: number
  detail?: string
  instance?: string
  code?: string
  params?: ErrorParams
  errors?: FieldViolation[]
  timestamp?: string
}

export type ApiErrorInfo = {
  /** `http`: the server answered with an error; `network`: no answer; `timeout`: too slow; `canceled`: aborted by the UI. */
  kind: 'http' | 'network' | 'timeout' | 'canceled' | 'unknown'
  status?: number
  code?: string
  params: ErrorParams
  errors: FieldViolation[]
  detail?: string
}

// ------------------------------------------------------------------ catalogue

type Message = string | ((params: ErrorParams) => string)

const MESSAGES: Record<string, Message> = {
  // generic / framework
  VALIDATION_FAILED: 'Certains champs sont invalides : {fields}.',
  MALFORMED_REQUEST: 'La requête envoyée est invalide. Rechargez la page puis réessayez.',
  INVALID_PARAMETER: 'La valeur du paramètre « {parameter} » est invalide.',
  MISSING_PARAMETER: 'Le paramètre « {parameter} » est obligatoire.',
  BAD_REQUEST: 'La requête est invalide.',
  UNAUTHORIZED: 'Votre session a expiré. Reconnectez-vous pour continuer.',
  FORBIDDEN: 'Vous n’avez pas les droits nécessaires pour effectuer cette action.',
  NOT_FOUND: 'L’élément demandé est introuvable. Il a peut-être été supprimé.',
  ENDPOINT_NOT_FOUND: 'Ce service est introuvable. L’application est peut-être en cours de mise à jour.',
  METHOD_NOT_ALLOWED: 'Cette opération n’est pas prise en charge.',
  NOT_ACCEPTABLE: 'Le format de réponse demandé n’est pas pris en charge.',
  CONFLICT: 'Cette action entre en conflit avec l’état actuel des données.',
  DATA_CONFLICT: 'Cet élément entre en conflit avec des données existantes ou est encore utilisé ailleurs.',
  CONCURRENT_MODIFICATION: 'Cet élément vient d’être modifié par quelqu’un d’autre. Rechargez-le puis réessayez.',
  PAYLOAD_TOO_LARGE: 'Les données envoyées sont trop volumineuses.',
  UNSUPPORTED_MEDIA_TYPE: 'Le format des données envoyées n’est pas pris en charge.',
  INTERNAL_ERROR: 'Une erreur inattendue est survenue sur le serveur. Réessayez dans un instant.',
  SERVICE_UNAVAILABLE: 'Le service est momentanément indisponible. Réessayez dans quelques instants.',

  // authentication
  AUTHENTICATION_REQUIRED: 'Votre session a expiré. Reconnectez-vous pour continuer.',
  INVALID_CREDENTIALS: 'Adresse e-mail ou mot de passe incorrect.',
  PASSWORD_CHANGE_REQUIRED: 'Modifiez votre mot de passe temporaire avant de continuer.',
  CURRENT_PASSWORD_INCORRECT: 'Le mot de passe actuel est incorrect.',
  PASSWORD_REUSED: 'Choisissez un mot de passe différent de l’actuel.',
  PASSWORD_RESET_TOKEN_INVALID: 'Ce lien de réinitialisation est invalide ou a expiré.',
  ACCOUNT_EMAIL_TAKEN: 'Un compte existe déjà avec cette adresse e-mail.',
  ORGANIZATION_NAME_TAKEN: 'Une organisation porte déjà ce nom.',
  SPRINT_NAME_TAKEN: 'Un sprint porte déjà ce nom dans ce projet.',
  SPRINT_ACTIVE_EXISTS: 'Un seul sprint peut être actif par projet.',
  SPRINT_DATES_INVALID: 'La date de fin du sprint doit suivre sa date de début.',
  SPRINT_TASK_PROJECT_MISMATCH: 'La tâche et le sprint doivent appartenir au même projet.',
  SPRINT_MANAGE_REQUIRES_TASK_ACCESS: 'La gestion des sprints nécessite les droits Scrum et de modification des tâches.',
  ORGANIZATION_EDIT_REQUIRES_VIEW: 'La modification du profil nécessite aussi le droit de consulter l’organisation.',
  EMPLOYEE_ACCOUNT_NOT_FOUND: 'Cet employé ne possède pas encore de compte actif.',

  // authorization
  ORGANIZATION_ADMIN_REQUIRED: 'Cette action est réservée aux administrateurs de l’organisation.',
  SPACE_PERMISSION_REQUIRED: 'Vous n’avez pas accès à cet espace.',
  PROJECT_PERMISSION_REQUIRED: 'Vous n’avez pas accès à ce projet.',
  RESOURCE_POOL_PERMISSION_REQUIRED: 'Vous n’avez pas le droit de consulter ce pool de ressources.',
  PERMISSION_REQUIRED: 'Vous n’avez pas la permission requise ({permission}).',
  SELF_ROLE_ASSIGNMENT_FORBIDDEN: 'Vous ne pouvez pas modifier les rôles de votre propre compte.',

  // roles & permissions
  ROLE_NOT_FOUND: 'Ce rôle est introuvable. Il a peut-être été supprimé.',
  ACTIVE_MEMBER_NOT_FOUND: 'Ce membre actif de l’organisation est introuvable.',
  RESERVED_ROLE_NAME: 'Ce nom de rôle est réservé par la plateforme.',
  ROLE_NAME_TAKEN: 'Un rôle portant ce nom existe déjà pour ce périmètre.',
  BUILT_IN_ROLE_IMMUTABLE: 'Les rôles prédéfinis ne peuvent pas être modifiés.',
  BUILT_IN_ROLE_UNDELETABLE: 'Les rôles prédéfinis ne peuvent pas être supprimés.',
  ROLE_IN_USE: 'Retirez ce rôle de ses utilisateurs avant de le supprimer.',
  SUPER_ADMIN_ASSIGNMENT_PROTECTED: 'L’attribution du super-administrateur de l’organisation est protégée.',
  SUPER_ADMIN_NOT_ASSIGNABLE: 'Le rôle de super-administrateur ne peut pas être attribué depuis la gestion des rôles.',
  ROLE_SCOPE_IMMUTABLE: 'Le périmètre d’un rôle ne peut pas être modifié.',
  ROLE_REQUIRED_FOR_SCOPE: 'Choisissez un rôle pour ce périmètre.',
  PERMISSION_SCOPE_MISMATCH: 'Cette permission ne peut pas être utilisée pour le périmètre choisi.',
  GANTT_PERMISSIONS_REQUIRE_VIEW: 'Les permissions de planning et de dépendances Gantt nécessitent aussi « gantt.view ».',
  WORKLOAD_MANAGE_REQUIRES_VIEW: 'La permission de gestion de la charge nécessite aussi « workload.view ».',
  UNKNOWN_PERMISSION: 'Une ou plusieurs permissions n’existent pas dans le catalogue.',

  // organizations & spaces
  ORGANIZATION_NOT_FOUND: 'Cette organisation est introuvable.',
  SPACE_NOT_FOUND: 'Cet espace est introuvable. Il a peut-être été supprimé.',
  SPACE_NOT_IN_ORGANIZATION: 'Cet espace n’appartient pas à votre organisation.',

  // employees & credentials
  EMPLOYEE_NOT_FOUND: 'Cet employé est introuvable. Il a peut-être été supprimé.',
  EMPLOYEE_NOT_IN_SPACE: 'Cet employé n’appartient pas à cet espace.',
  EMPLOYEE_ALREADY_HAS_ACCOUNT: 'Cet employé possède déjà un compte.',
  EMPLOYEE_EMAIL_MISSING: 'Cet employé n’a pas d’adresse e-mail. Ajoutez-en une avant d’envoyer les identifiants.',
  EMPLOYEE_EMAIL_INVALID: 'Saisissez une adresse e-mail valide pour cet employé.',
  EMPLOYEE_EMAIL_LOCKED: 'L’adresse e-mail ne peut plus être modifiée une fois le compte créé.',
  EMPLOYEE_EMAIL_ACCOUNT_MISMATCH: 'L’adresse e-mail de l’employé ne correspond pas à celle de son compte.',
  EMAIL_ALREADY_IN_USE: 'Cette adresse e-mail est déjà utilisée par un autre employé ou un autre compte.',
  EMPLOYEE_MANAGED_IN_DIRECTORY: 'Les employés se gèrent depuis l’annuaire des employés.',
  INVITATION_ROLE_REQUIRED: 'Choisissez un rôle d’accès avant d’inviter cet employé.',
  INVITATION_ROLE_UNAVAILABLE: 'Choisissez un rôle disponible pour le périmètre de cet employé.',
  INVITATION_ROLE_NOT_CUSTOM: 'Choisissez un rôle d’accès personnalisé.',
  INVITATION_ROLE_EMPTY: 'Choisissez un rôle comportant au moins une permission.',
  INVITATION_ORGANIZATION_ROLE_REQUIRED: 'Choisissez un rôle d’organisation.',
  INVITATION_SPACE_ROLE_REQUIRED: 'Choisissez un rôle pour l’espace de l’employé.',
  INVITATION_PROJECT_ROLE_FORBIDDEN: 'Les rôles de projet ne peuvent pas être attribués lors d’une invitation.',
  INVITATION_REQUIRED: 'Choisissez un rôle d’accès et invitez cet employé individuellement.',
  CREDENTIALS_ALREADY_SENT: 'Les identifiants ont déjà été envoyés.',
  EMAIL_DELIVERY_FAILED: 'L’envoi de l’e-mail a échoué. Vérifiez la configuration SMTP puis réessayez.',
  CREDENTIAL_DELIVERY_FAILED: 'Impossible d’envoyer les identifiants. Réessayez pour cet employé.',

  // resources
  RESOURCE_NOT_FOUND: 'Cette ressource est introuvable. Elle a peut-être été supprimée.',
  RESOURCE_NOT_IN_SPACE: 'Cette ressource n’appartient pas à cet espace.',
  RESOURCE_UNAVAILABLE: 'Cette ressource n’est pas disponible.',
  RESOURCE_ASSIGNED_TO_PROJECTS: 'Retirez cette ressource de ses projets avant de la supprimer.',
  EQUIPMENT_FIELDS_INVALID: 'Un équipement nécessite une quantité et n’accepte pas de montant.',
  BUDGET_FIELDS_INVALID: 'Un budget nécessite un montant et n’accepte pas de quantité.',

  // projects & project resource pool
  PROJECT_NOT_FOUND: 'Ce projet est introuvable. Il a peut-être été supprimé.',
  PROJECT_RESOURCE_NOT_FOUND: 'Cette ressource n’est pas affectée à ce projet.',
  PLANNED_DATES_INVALID: 'La date de fin prévue ne peut pas précéder la date de début prévue.',
  PROJECT_HAS_RESOURCES: 'Retirez les ressources du projet avant de le déplacer vers un autre espace.',
  PROJECT_DATES_EXCEED_EQUIPMENT: 'Les dates du projet dépassent la disponibilité de l’équipement « {resource} ».',
  PROJECT_STARTS_AFTER_TASK: 'Le projet ne peut pas commencer après le début prévu de la tâche « {task} ».',
  PROJECT_ENDS_BEFORE_TASK: 'Le projet ne peut pas se terminer avant la fin prévue de la tâche « {task} ».',
  PROJECT_ENDED_RESOURCE_LOCKED: 'Impossible d’affecter des ressources à un projet terminé.',
  PROJECT_ENDED_BUDGET_LOCKED: 'Le budget ne peut plus être retiré une fois le projet terminé.',
  RESOURCE_OUTSIDE_PROJECT_SPACE: 'Un projet ne peut utiliser que les ressources de son propre espace.',
  RESOURCE_ALREADY_IN_PROJECT_POOL: 'Cette ressource fait déjà partie du pool du projet.',
  RESOURCE_IN_USE_BY_TASKS: 'Retirez cette ressource de ses tâches avant de la retirer du projet.',
  EQUIPMENT_QUANTITY_EXCEEDED: 'Cette affectation dépasse la quantité d’équipement disponible.',
  BUDGET_EXCEEDED: 'Cette affectation dépasse le budget restant.',
  EMPLOYEE_POOL_ALLOCATION_INVALID: 'Un employé est ajouté au pool du projet en tant que personne, sans quantité ni montant.',
  EQUIPMENT_QUANTITY_REQUIRED: 'Indiquez le nombre d’unités d’équipement à affecter.',
  BUDGET_AMOUNT_REQUIRED: 'Indiquez le montant du budget à affecter.',

  // tasks & task resources
  TASK_NOT_FOUND: 'Cette tâche est introuvable. Elle a peut-être été supprimée.',
  TASK_RESOURCE_NOT_FOUND: 'Cette ressource n’est pas affectée à cette tâche.',
  TASK_HAS_DEPENDENCIES: 'Supprimez les dépendances de cette tâche avant de la déplacer vers un autre projet.',
  TASK_BUDGET_COMMITTED: 'Une tâche terminée ayant consommé du budget ne peut pas être supprimée.',
  ACTUAL_START_IN_FUTURE: 'La date de début réelle ne peut pas être dans le futur.',
  ACTUAL_END_IN_FUTURE: 'La date de fin réelle ne peut pas être dans le futur.',
  ACTUAL_DATES_INVALID: 'La date de fin réelle ne peut pas précéder la date de début réelle.',
  PROGRESS_OUT_OF_RANGE: 'L’avancement doit être compris entre 0 et 100 %.',
  TASK_STARTS_BEFORE_PROJECT: 'La tâche ne peut pas commencer avant le début prévu du projet.',
  TASK_ENDS_AFTER_PROJECT: 'La tâche ne peut pas se terminer après la fin prévue du projet.',
  TASK_STARTS_AFTER_PROJECT_END: 'La tâche ne peut pas commencer après la fin prévue du projet.',
  RESOURCE_NOT_IN_PROJECT_POOL: 'Ajoutez d’abord cette ressource au pool du projet.',
  TASK_RESOURCE_NOT_IN_TARGET_POOL: 'Les ressources de la tâche doivent faire partie du pool du projet de destination.',
  TASK_COMPLETED_RESOURCE_LOCKED: 'Impossible d’affecter des ressources à une tâche terminée.',
  TASK_PERIOD_ENDED: 'Impossible d’affecter des ressources à une tâche dont la période planifiée est terminée.',
  TASK_ENDED_BUDGET_LOCKED: 'Le budget ne peut plus être retiré une fois la tâche terminée.',
  RESOURCE_ALREADY_ON_TASK: 'Cette ressource est déjà affectée à la tâche.',
  EMPLOYEE_TASK_ALLOCATION_INVALID: 'Un employé est affecté avec un pourcentage de son temps, sans quantité ni montant.',
  PROJECT_UNITS_EXCEEDED: 'Cette affectation dépasse le nombre d’unités allouées au projet.',
  PROJECT_BUDGET_EXCEEDED: 'Cette affectation dépasse le budget disponible du projet.',
  BUDGET_AMOUNT_NOT_POSITIVE: 'Le montant du budget doit être supérieur à zéro.',

  // dependencies & scheduling
  DEPENDENCY_NOT_FOUND: 'Cette dépendance est introuvable. Elle a peut-être été supprimée.',
  TASKS_ALREADY_LINKED: 'Ces tâches sont déjà liées.',
  DEPENDENCY_CYCLE: 'Cette dépendance créerait un cycle entre les tâches.',
  SELF_DEPENDENCY: 'Une tâche ne peut pas dépendre d’elle-même.',
  DEPENDENCY_CROSS_PROJECT: 'Les deux tâches doivent appartenir au même projet.',
  TASK_RESCHEDULE_BLOCKED: params => {
    const reason = typeof params.causeCode === 'string' ? translateErrorCode(params.causeCode, params) : undefined
    return `Impossible de replanifier la tâche « ${String(params.task ?? '')} »${reason ? ` : ${lowerFirst(reason)}` : '.'}`
  },

  // workload
  TASK_ASSIGNMENT_NOT_FOUND: 'Cette affectation est introuvable. Elle a peut-être été supprimée.',
  PERIOD_INVALID: 'La fin de la période ne peut pas précéder son début.',
  ONLY_EMPLOYEES_REASSIGNABLE: 'Seules les affectations d’employés peuvent être réattribuées.',
  COMPLETED_TASK_NOT_REASSIGNABLE: 'Une tâche terminée ne peut pas être réattribuée.',
  SAME_EMPLOYEE_REASSIGNMENT: 'Choisissez un autre employé.',
  EMPLOYEE_UNAVAILABLE: 'Cet employé n’est pas disponible.',
  EMPLOYEE_ALREADY_ON_TASK: '{employee} est déjà affecté(e) à cette tâche.',
  TASK_ALREADY_STARTED: 'Seule une tâche non commencée peut être décalée : « {task} ».',
  TASK_NOT_SCHEDULED: 'La tâche « {task} » n’a pas de dates planifiées.',
  SHIFT_EXCEEDS_MARGIN: params => format(
    'Décaler « {task} » de {days} jour(s) dépasse sa marge {marginLabel} ({margin} jour(s)). Recalculez la proposition.',
    { ...params, marginLabel: params.marginType === 'free' ? 'libre' : 'totale' }),
  INVALID_SHIFT: 'Le décalage proposé est invalide.',
  INVALID_REASSIGNMENT: 'La réattribution proposée est invalide.',
  UNKNOWN_LEVELING_ACTION: 'Action de lissage inconnue : {action}.',
}

/** Codes too vague to beat the caller's own fallback ("Impossible d'enregistrer ce projet."). */
const GENERIC_CODES = new Set(['BAD_REQUEST', 'NOT_FOUND', 'CONFLICT'])

const NETWORK_MESSAGE = 'Impossible de joindre le serveur. Vérifiez votre connexion puis réessayez.'
const TIMEOUT_MESSAGE = 'Le serveur met trop de temps à répondre. Réessayez dans un instant.'

/** Used when the server answers without a known code (proxy errors, older servers...). */
const STATUS_MESSAGES: Record<number, string> = {
  401: MESSAGES.UNAUTHORIZED as string,
  403: MESSAGES.FORBIDDEN as string,
  408: TIMEOUT_MESSAGE,
  413: MESSAGES.PAYLOAD_TOO_LARGE as string,
  429: 'Trop de requêtes. Patientez quelques instants avant de réessayer.',
  500: MESSAGES.INTERNAL_ERROR as string,
  502: 'Le serveur est momentanément injoignable. Réessayez dans quelques instants.',
  503: MESSAGES.SERVICE_UNAVAILABLE as string,
  504: TIMEOUT_MESSAGE,
}

// ------------------------------------------------------------------ field validation

const FIELD_LABELS: Record<string, string> = {
  name: 'Nom', displayName: 'Nom affiché', organizationName: 'Nom de l’organisation', email: 'Adresse e-mail',
  password: 'Mot de passe', currentPassword: 'Mot de passe actuel', newPassword: 'Nouveau mot de passe',
  description: 'Description', jobTitle: 'Poste', status: 'Statut', type: 'Type', scope: 'Périmètre',
  quantity: 'Quantité', amount: 'Montant', allocationPercent: 'Pourcentage d’affectation', days: 'Nombre de jours',
  progress: 'Avancement', startDate: 'Date de début réelle', endDate: 'Date de fin réelle',
  baselineStart: 'Date de début prévue', baselineEnd: 'Date de fin prévue', from: 'Début de période', to: 'Fin de période',
  spaceId: 'Espace', projectId: 'Projet', taskId: 'Tâche', resourceId: 'Ressource', roleId: 'Rôle', roleScope: 'Périmètre du rôle',
  userId: 'Utilisateur', contextId: 'Contexte', fromResourceId: 'Employé d’origine', toResourceId: 'Employé de destination',
  predecessorTaskId: 'Tâche prédécesseur', successorTaskId: 'Tâche successeur', permissionKeys: 'Permissions',
  employees: 'Employés', actions: 'Actions', rowNumber: 'Numéro de ligne', lagDays: 'Décalage (jours)',
}

const MAX_INT = 2147483647

function fieldLabel(path: string): string {
  const leaf = path.split('.').pop()?.replace(/\[\d+\]$/, '') ?? path
  return FIELD_LABELS[leaf] ?? leaf
}

/** One French sentence for a single invalid field. */
export function describeFieldViolation(violation: FieldViolation): string {
  const label = fieldLabel(violation.field)
  const attributes = violation.attributes ?? {}
  const min = Number(attributes.min ?? 0)
  const max = Number(attributes.max ?? MAX_INT)
  switch (violation.constraint) {
    case 'NotNull':
    case 'NotBlank':
    case 'NotEmpty':
      return `Le champ « ${label} » est obligatoire.`
    case 'Size':
    case 'Length':
      if (min > 0 && max < MAX_INT) return `« ${label} » doit contenir entre ${min} et ${max} caractères.`
      if (min > 0) return `« ${label} » doit contenir au moins ${min} caractères.`
      return `« ${label} » ne doit pas dépasser ${max} caractères.`
    case 'Email':
      return `« ${label} » doit être une adresse e-mail valide.`
    case 'Min':
    case 'DecimalMin':
      return `« ${label} » doit être supérieur ou égal à ${String(attributes.value ?? '')}.`
    case 'Max':
    case 'DecimalMax':
      return `« ${label} » doit être inférieur ou égal à ${String(attributes.value ?? '')}.`
    case 'Positive':
      return `« ${label} » doit être supérieur à zéro.`
    case 'PositiveOrZero':
      return `« ${label} » ne peut pas être négatif.`
    case 'Pattern':
      return `« ${label} » n’a pas le format attendu.`
    case 'Past':
    case 'PastOrPresent':
      return `« ${label} » ne peut pas être dans le futur.`
    case 'Future':
    case 'FutureOrPresent':
      return `« ${label} » ne peut pas être dans le passé.`
    default:
      return `La valeur de « ${label} » est invalide.`
  }
}

// ------------------------------------------------------------------ public API

function format(template: string, params: ErrorParams): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) => (params[key] === undefined || params[key] === null ? match : String(params[key])))
}

function lowerFirst(text: string): string {
  return text.charAt(0).toLowerCase() + text.slice(1)
}

/** French message for an error code, or `undefined` if the code is unknown. */
export function translateErrorCode(code: string | null | undefined, params: ErrorParams = {}, errors: FieldViolation[] = []): string | undefined {
  if (!code) return undefined
  if (code === 'VALIDATION_FAILED' && errors.length > 0) {
    const sentences = [...new Set(errors.map(describeFieldViolation))]
    return sentences.length <= 3 ? sentences.join(' ') : `${sentences.slice(0, 3).join(' ')} (+${sentences.length - 3} autre(s) erreur(s))`
  }
  if (code === 'VALIDATION_FAILED' && typeof params.fields === 'string') {
    return format(MESSAGES.VALIDATION_FAILED as string, { fields: params.fields.split(', ').map(fieldLabel).join(', ') })
  }
  const message = MESSAGES[code]
  if (message === undefined) return undefined
  return typeof message === 'function' ? message(params) : format(message, params)
}

/** Extracts what the UI needs from any thrown value (axios error, Error, anything). */
export function parseApiError(error: unknown): ApiErrorInfo {
  if (axios.isCancel(error)) return { kind: 'canceled', params: {}, errors: [] }
  if (axios.isAxiosError(error)) {
    if (error.code === 'ERR_CANCELED') return { kind: 'canceled', params: {}, errors: [] }
    if (!error.response) {
      const timedOut = error.code === 'ECONNABORTED' || error.code === 'ETIMEDOUT'
      return { kind: timedOut ? 'timeout' : 'network', params: {}, errors: [] }
    }
    const body = (typeof error.response.data === 'object' && error.response.data !== null ? error.response.data : {}) as ApiProblem
    return {
      kind: 'http',
      status: error.response.status,
      code: typeof body.code === 'string' ? body.code : undefined,
      params: body.params ?? {},
      errors: Array.isArray(body.errors) ? body.errors : [],
      detail: body.detail,
    }
  }
  if (error instanceof DOMException && error.name === 'AbortError') return { kind: 'canceled', params: {}, errors: [] }
  return { kind: 'unknown', params: {}, errors: [], detail: error instanceof Error ? error.message : undefined }
}

/** True when the error is an API error with the given code, e.g. `hasErrorCode(cause, 'DEPENDENCY_CYCLE')`. */
export function hasErrorCode(error: unknown, code: string): boolean {
  return parseApiError(error).code === code
}

/**
 * The message to show the user for a failed request. `fallback` describes what failed in this screen
 * ("Impossible d'enregistrer ce projet.") and is used when the server gives no specific reason.
 */
export function getApiErrorMessage(error: unknown, fallback: string): string {
  const info = parseApiError(error)
  switch (info.kind) {
    case 'network': return NETWORK_MESSAGE
    case 'timeout': return TIMEOUT_MESSAGE
    case 'canceled': return fallback
    case 'unknown': return fallback
  }
  if (info.code && !GENERIC_CODES.has(info.code)) {
    const translated = translateErrorCode(info.code, info.params, info.errors)
    if (translated) return info.status !== undefined && info.status >= 500 ? `${fallback} ${translated}` : translated
  }
  const byStatus = info.status === undefined ? undefined : STATUS_MESSAGES[info.status] ?? (info.status >= 500 ? STATUS_MESSAGES[500] : undefined)
  if (byStatus) return info.status !== undefined && info.status >= 500 ? `${fallback} ${byStatus}` : byStatus
  return fallback
}

/** French reason for a SKIPPED/FAILED row returned by batch endpoints (employee import, credential sending). */
export function describeResultReason(row: { code?: string | null; params?: ErrorParams | null }, fallback: string): string {
  return translateErrorCode(row.code, row.params ?? {}) ?? fallback
}
