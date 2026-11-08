-- Las inscripciones se buscan por categoría para listar los equipos de una categoría (API GraphQL).
CREATE INDEX ix_team_registrations_category ON team_registrations (competition_id, category_id);
