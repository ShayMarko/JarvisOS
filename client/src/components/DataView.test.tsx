import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { DataView } from './DataView'

describe('<DataView>', () => {
  it('humanizes keys and renders scalar values', () => {
    render(<DataView value={{ totalRuns: 3, isActive: true, agentName: 'General' }} />)
    expect(screen.getByText('Total Runs')).toBeInTheDocument()
    expect(screen.getByText('3')).toBeInTheDocument()
    expect(screen.getByText('Agent Name')).toBeInTheDocument()
    expect(screen.getByText('General')).toBeInTheDocument()
    expect(screen.getByText('yes')).toBeInTheDocument()   // boolean → yes/no
  })

  it('renders a scalar array as a list', () => {
    render(<DataView value={['alpha', 'beta']} />)
    expect(screen.getByText('alpha')).toBeInTheDocument()
    expect(screen.getByText('beta')).toBeInTheDocument()
  })
})
