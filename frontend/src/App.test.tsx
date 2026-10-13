import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('switches between the teams, rounds and standings sections', async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Registrar equipo' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Rondas' }));
    expect(screen.getByRole('heading', { name: 'Programar ronda' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Registrar equipo' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Posiciones' }));
    expect(screen.getByRole('heading', { name: 'Posiciones' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Equipos' }));
    expect(screen.getByRole('heading', { name: 'Registrar equipo' })).toBeInTheDocument();
  });
});
