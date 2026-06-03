import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AgentsWindow } from './AgentsWindow'

// Mock the API module — the component only needs getAgents.
vi.mock('../../api', () => ({
  getAgents: () => Promise.resolve([
    { name: 'General Assistant', slug: 'general', role: 'Handles requests', category: 'general' },
    { name: 'Code Agent', slug: 'code', role: 'Writes code', category: 'dev' },
  ]),
}))

describe('<AgentsWindow>', () => {
  it('starts with categories COLLAPSED — headers show, agents hidden', async () => {
    render(<AgentsWindow />)
    expect(await screen.findByText('Development')).toBeInTheDocument()   // category header
    expect(screen.getByText('General')).toBeInTheDocument()
    expect(screen.queryByText('Code Agent')).not.toBeInTheDocument()     // collapsed → hidden
  })

  it('expands a category on click to reveal its agents', async () => {
    render(<AgentsWindow />)
    fireEvent.click(await screen.findByText('Development'))
    expect(screen.getByText('Code Agent')).toBeInTheDocument()
  })

  it('search reveals matching agents even while categories are collapsed', async () => {
    render(<AgentsWindow />)
    await screen.findByText('Development')
    fireEvent.change(screen.getByPlaceholderText(/search agents/i), { target: { value: 'code' } })
    expect(screen.getByText('Code Agent')).toBeInTheDocument()           // surfaced by search
    expect(screen.queryByText('General Assistant')).not.toBeInTheDocument()
  })
})
