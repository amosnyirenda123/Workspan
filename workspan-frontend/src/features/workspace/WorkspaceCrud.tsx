import { useState } from 'react'
import { ProjectsManager } from '../projects/ProjectsManager'
import { SpacesManager } from '../spaces/SpacesManager'
import { TasksManager } from '../tasks/TasksManager'

type Section = 'spaces' | 'projects' | 'tasks'

export function WorkspaceCrud({ onChange }: { onChange: () => void }) {
  const [section, setSection] = useState<Section>('spaces')

  return <section className="workspace-crud">
    <div className="crud-heading"><div><p className="eyebrow">GESTION DU TRAVAIL</p><h2>Espaces, projets et tâches</h2><p>Créez les éléments dans cet ordre : espace, projet, puis tâche.</p></div><button className="secondary-button" onClick={onChange}>Actualiser le résumé</button></div>
    <div className="crud-tabs" role="tablist" aria-label="Ressources de l’espace de travail">
      <button role="tab" aria-selected={section === 'spaces'} className={section === 'spaces' ? 'active' : ''} onClick={() => setSection('spaces')}>Espaces</button>
      <button role="tab" aria-selected={section === 'projects'} className={section === 'projects' ? 'active' : ''} onClick={() => setSection('projects')}>Projets</button>
      <button role="tab" aria-selected={section === 'tasks'} className={section === 'tasks' ? 'active' : ''} onClick={() => setSection('tasks')}>Tâches</button>
    </div>
    {section === 'spaces' && <SpacesManager onChange={onChange} />}
    {section === 'projects' && <ProjectsManager onChange={onChange} />}
    {section === 'tasks' && <TasksManager onChange={onChange} />}
  </section>
}
