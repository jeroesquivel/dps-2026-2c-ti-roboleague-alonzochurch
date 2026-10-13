import { ApiRequestError } from '../../api/http';

export function ProblemList({ problems }: { problems: string[] }) {
  if (problems.length === 0) {
    return null;
  }
  return (
    <div className="problems" role="alert">
      <p>Revisá los datos antes de enviar:</p>
      <ul>
        {problems.map((problem) => (
          <li key={problem}>{problem}</li>
        ))}
      </ul>
    </div>
  );
}

export function ApiProblem({ title, error }: { title: string; error: ApiRequestError }) {
  return (
    <section className="result result--error" role="alert">
      <h3>{title}</h3>
      <p>{error.message}</p>
      <p className="code">Código: {error.code}</p>
    </section>
  );
}

export function asApiError(failure: unknown): ApiRequestError {
  return failure instanceof ApiRequestError
    ? failure
    : new ApiRequestError('UNEXPECTED_ERROR', 'Ocurrió un error inesperado.', 0);
}
