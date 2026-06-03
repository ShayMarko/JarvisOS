import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
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

  it('routes a leaked raw tool-call answer through the friendly error card', () => {
    const turns: Turn[] = [{
      id: 't2', prompt: 'whatsapp', loading: false, steps: [],
      resp: resp({ answer: '{"name":"x","parameters":{}}' }),
    }]
    render(<ConversationWindow turns={turns} onClear={() => {}} />)
    expect(screen.getByText('Something went wrong')).toBeInTheDocument()
  })
})
