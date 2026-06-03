import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ErrorCard } from './ErrorCard'

describe('<ErrorCard>', () => {
  it('shows a friendly message, never the raw error', () => {
    render(<ErrorCard message="Error: connection refused" />)
    expect(screen.getByText('Something went wrong')).toBeInTheDocument()
    expect(screen.getByText(/reach that service/i)).toBeInTheDocument()
  })
})
