import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import App from './App';

describe('App', () => {
  it('switches between the event, teams, rounds, standings, appeals, score and audit sections', async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(screen.getByRole('heading', { name: 'Configurar el evento' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Equipos' }));
    expect(screen.getByRole('heading', { name: 'Registrar equipo' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Configurar el evento' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Rondas' }));
    expect(screen.getByRole('heading', { name: 'Programar ronda' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Registrar equipo' })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Posiciones' }));
    expect(screen.getByRole('heading', { name: 'Posiciones' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Apelaciones' }));
    expect(screen.getByRole('heading', { name: 'Apelaciones' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Puntaje' }));
    expect(screen.getByRole('heading', { name: 'Puntaje de una corrida' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Auditoría' }));
    expect(screen.getByRole('heading', { name: 'Auditoría de una operación' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Equipos' }));
    expect(screen.getByRole('heading', { name: 'Registrar equipo' })).toBeInTheDocument();
  });
});
