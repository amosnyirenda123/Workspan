import { useEffect, useState, type FormEvent } from 'react'
import { getApiErrorMessage } from '../../api/http'
import { taskService } from '../../api/taskService'
import type { Task, TaskActivity, TaskComment } from '../../api/types'
import './task-comments.css'

export function TaskCommentsPanel({ task, canComment, onClose }: { task: Task; canComment: boolean; onClose: () => void }) {
  const [items, setItems] = useState<TaskComment[]>([])
  const [events, setEvents] = useState<TaskActivity[]>([])
  const [body, setBody] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    let active = true
    Promise.all([taskService.comments(task.id), taskService.activity(task.id)])
      .then(([comments, activity]) => { if (active) { setItems(comments); setEvents(activity) } })
      .catch(cause => { if (active) setError(getApiErrorMessage(cause, 'Unable to load task discussion.')) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [task.id])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSaving(true); setError('')
    try {
      const item = await taskService.addComment(task.id, body)
      setItems(current => [...current, item]); setBody('')
      setEvents(await taskService.activity(task.id))
    } catch (cause) { setError(getApiErrorMessage(cause, 'Unable to add the comment.')) }
    finally { setSaving(false) }
  }

  return <div className="task-comments-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose() }}>
    <section className="task-comments-panel" role="dialog" aria-modal="true" aria-labelledby="task-comments-title">
      <header><div><span className="task-workspace-eyebrow">DISCUSSION AND HISTORY</span><h2 id="task-comments-title">{task.name}</h2><p>Team comments and recent changes</p></div><button type="button" aria-label="Close" onClick={onClose}>×</button></header>
      <div className="task-comments-list">{loading ? <p>Loading…</p> : <>
        <section><h3>Comments</h3>{items.length ? items.map(item => <article key={item.id}><div><strong>{item.authorName}</strong><time>{new Date(item.createdAt).toLocaleString('en-GB')}</time></div><p>{item.body}</p></article>) : <p className="task-comments-empty">No comments yet.</p>}</section>
        <section className="task-activity-list"><h3>Activity</h3>{events.length ? events.map(item => <article key={item.id}><div><strong>{item.actorName}</strong><time>{new Date(item.createdAt).toLocaleString('en-GB')}</time></div><p>{item.summary}</p></article>) : <p>No activity recorded.</p>}</section>
      </>}</div>
      {error && <div className="alert" role="alert">{error}</div>}
      {canComment && <form onSubmit={submit}><label htmlFor="task-comment-body">Add a comment</label><textarea id="task-comment-body" maxLength={2000} required value={body} onChange={event => setBody(event.target.value)} placeholder="Write a comment…"/><button className="primary-button" disabled={saving || !body.trim()}>{saving ? 'Sending…' : 'Post comment'}</button></form>}
    </section>
  </div>
}
