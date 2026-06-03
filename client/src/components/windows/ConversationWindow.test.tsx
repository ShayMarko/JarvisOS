import { describe, it, expect } from 'vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { ConversationWindow } from './ConversationWindow'
import type { Turn } from '../../types'
import type { ChatResponse } from '../../api'

function resp(over: Partial<ChatResponse>): ChatResponse {
  return { answer: 'ok', agent: 'General', steps: [], taskId: 't1', tokens: 10, model: 'ollama:llama3.2:3b', ...over }
}

describe('<ConversationWindow>', () => {
  it('shows the empty-state prompt when there are no turns', () => {
    render(<ConversationWindow turns={[]} onClear={() => {}} />)
    expect(screen.getByText(/your whole conversation shows up here/i)).toBeInTheDocument()
  })

  it('renders an answer and flags a cached response with its age', () => {
    const turns: Turn[] = [{
      id: 't1', prompt: 'explain recursion', loading: false, steps: [],
      resp: resp({ answer: 'Recursion is a function calling itself.', model: 'cache:120' }),
    }]
    render(<ConversationWindow turns={turns} onClear={() => {}} />)
    expect(screen.getByText('Recursion is a function calling itself.')).toBeInTheDocument()
    expect(screen.getByText(/cached answer · ~2 min old/i)).toBeInTheDocument()
  })

  it('toggles the trace between Steps (list) and Tree (diagram) views', () => {
    const turns: Turn[] = [{
      id: 't3', prompt: 'who am I', loading: false,
      steps: [{ kind: 'agent', label: 'Routed to Knowledge Librarian', detail: 'recall' },
              { kind: 'tool', label: 'profile_search', detail: 'name' },
              { kind: 'answer', label: 'Composed the answer', detail: '' }],
      resp: resp({ answer: 'You are Shay Marko.' }),
    }]
    const { container } = render(<ConversationWindow turns={turns} onClear={() => {}} />)
    // Default = Steps (list): the .substeps list exists, no .treeview diagram.
    expect(container.querySelector('.substeps')).toBeTruthy()
    expect(container.querySelector('.treeview')).toBeNull()
    // Switch to Tree → top-down org-chart appears, flat list goes away.
    fireEvent.click(screen.getByText('Tree'))
    expect(container.querySelector('.treeview')).toBeTruthy()
    expect(container.querySelector('.substeps')).toBeNull()
    expect(container.querySelectorAll('.tn').length).toBe(4)            // root, agent, tool, answer
    // the tool nests UNDER the agent → a 3rd-level <ul> (deep branch) exists.
    expect(container.querySelectorAll('.treeview ul ul ul').length).toBe(1)
  })

  it('routes a leaked raw tool-call answer through the friendly error card', () => {
    const turns: Turn[] = [{
      id: 't2', prompt: 'whatsapp', loading: false, steps: [],
      resp: resp({ answer: '{"name":"x","parameters":{}}' }),
    }]
    render(<ConversationWindow turns={turns} onClear={() => {}} />)
    expect(screen.getByText('Something went wrong')).toBeInTheDocument()
  })
})
