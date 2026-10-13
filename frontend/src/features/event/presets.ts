import type { RulebookJson } from './types';

/**
 * Reglamentos de ejemplo en el contrato HTTP de la API. Son sólo un punto de partida editable: el
 * servidor valida cada reglamento enviado y la pantalla no interpreta sus reglas.
 */
export interface RulebookPreset {
  id: string;
  label: string;
  rulebook: RulebookJson;
}

export const RULEBOOK_PRESETS: RulebookPreset[] = [
  {
    id: 'rescue',
    label: 'Rescate simple',
    rulebook: {
      "challenges": [
        {
          "id": "RESCUE",
          "name": "Rescue mission",
          "metrics": [
            {
              "key": "TIME",
              "kind": "TIME_SECONDS",
              "unit": "s",
              "requirement": "REQUIRED"
            },
            {
              "key": "OBJECTIVES",
              "kind": "OBJECTIVE_COUNT",
              "unit": "objectives",
              "requirement": "REQUIRED"
            },
            {
              "key": "ENERGY",
              "kind": "RESOURCE_UNITS",
              "unit": "mAh",
              "requirement": "REQUIRED"
            },
            {
              "key": "DESIGN",
              "kind": "JUDGE_CRITERION",
              "unit": "points",
              "requirement": "OPTIONAL"
            }
          ],
          "scoringRules": [
            {
              "type": "TIME",
              "metric": "TIME",
              "referenceSeconds": 120,
              "pointsPerSecondSaved": 0.5,
              "maximumPoints": 30
            },
            {
              "type": "OBJECTIVES",
              "metric": "OBJECTIVES",
              "pointsPerObjective": 10,
              "maximumObjectives": 5
            },
            {
              "type": "JUDGES",
              "criterion": "DESIGN",
              "weight": 1
            },
            {
              "type": "RESOURCE",
              "metric": "ENERGY",
              "allowance": 50,
              "pointsPerUnitOver": 1
            },
            {
              "type": "BONUS",
              "metric": "OBJECTIVES",
              "comparison": "AT_LEAST",
              "threshold": 5,
              "bonusPoints": 15
            }
          ],
          "penalties": [
            {
              "code": "RESTART",
              "description": "manual restart",
              "pointsDeducted": 3
            },
            {
              "code": "OUT_OF_BOUNDS",
              "description": "robot out of bounds",
              "pointsDeducted": 5
            }
          ],
          "maximumAttempts": 2,
          "bestRounds": null,
          "bonusCap": null,
          "mixedSources": false
        }
      ],
      "eligibility": [
        {
          "type": "AGE_RANGE"
        },
        {
          "type": "TEAM_COMPOSITION",
          "minimumCompetitors": 2,
          "maximumCompetitors": 4,
          "coachRequiredUnderAge": 18
        },
        {
          "type": "ROBOT_CLASS"
        },
        {
          "type": "ROBOT_SPECIFICATION",
          "maximumWeightKg": 3,
          "maximumDimensionsMm": {
            "length": 200,
            "width": 200,
            "height": 200
          }
        },
        {
          "type": "REQUIRED_DOCUMENTS",
          "documents": [
            "PARENTAL_CONSENT",
            "TECHNICAL_SHEET"
          ]
        }
      ],
      "attemptAggregation": {
        "type": "BEST_ATTEMPT"
      },
      "tiebreaks": [
        {
          "type": "HIGHEST_SINGLE_RUN"
        },
        {
          "type": "FEWEST_PENALTIES"
        },
        {
          "type": "FASTEST_METRIC",
          "metric": "TIME"
        }
      ],
      "appealWindowMinutes": 60
    },
  },
  {
    id: 'mixed',
    label: 'Rescate (mejores 2 de 3, tope de bonificaciones) y desafío mixto',
    rulebook: {
      "challenges": [
        {
          "id": "RESCUE",
          "name": "Rescue mission",
          "metrics": [
            {
              "key": "TIME",
              "kind": "TIME_SECONDS",
              "unit": "s",
              "requirement": "REQUIRED"
            },
            {
              "key": "OBJECTIVES",
              "kind": "OBJECTIVE_COUNT",
              "unit": "objectives",
              "requirement": "REQUIRED"
            },
            {
              "key": "ENERGY",
              "kind": "RESOURCE_UNITS",
              "unit": "mAh",
              "requirement": "REQUIRED"
            },
            {
              "key": "DESIGN",
              "kind": "JUDGE_CRITERION",
              "unit": "points",
              "requirement": "OPTIONAL"
            }
          ],
          "scoringRules": [
            {
              "type": "TIME",
              "metric": "TIME",
              "referenceSeconds": 120,
              "pointsPerSecondSaved": 0.5,
              "maximumPoints": 30
            },
            {
              "type": "OBJECTIVES",
              "metric": "OBJECTIVES",
              "pointsPerObjective": 10,
              "maximumObjectives": 5
            },
            {
              "type": "JUDGES",
              "criterion": "DESIGN",
              "weight": 1
            },
            {
              "type": "RESOURCE",
              "metric": "ENERGY",
              "allowance": 50,
              "pointsPerUnitOver": 1
            },
            {
              "type": "BONUS",
              "metric": "TIME",
              "comparison": "AT_MOST",
              "threshold": 60,
              "bonusPoints": 10
            },
            {
              "type": "BONUS",
              "metric": "OBJECTIVES",
              "comparison": "AT_LEAST",
              "threshold": 5,
              "bonusPoints": 15
            }
          ],
          "penalties": [
            {
              "code": "RESTART",
              "description": "manual restart",
              "pointsDeducted": 3
            },
            {
              "code": "OUT_OF_BOUNDS",
              "description": "robot out of bounds",
              "pointsDeducted": 5
            }
          ],
          "maximumAttempts": 2,
          "bestRounds": {
            "counted": 2,
            "outOf": 3
          },
          "bonusCap": {
            "maximumPoints": 25
          },
          "mixedSources": false
        },
        {
          "id": "SHOWCASE",
          "name": "Showcase",
          "metrics": [
            {
              "key": "TIME",
              "kind": "TIME_SECONDS",
              "unit": "s",
              "requirement": "REQUIRED"
            },
            {
              "key": "ACCURACY",
              "kind": "PRECISION_RATIO",
              "unit": "ratio",
              "requirement": "REQUIRED"
            },
            {
              "key": "CREATIVITY",
              "kind": "JUDGE_CRITERION",
              "unit": "points",
              "requirement": "REQUIRED"
            },
            {
              "key": "EXECUTION",
              "kind": "JUDGE_CRITERION",
              "unit": "points",
              "requirement": "REQUIRED"
            }
          ],
          "scoringRules": [
            {
              "type": "TIME",
              "metric": "TIME",
              "referenceSeconds": 90,
              "pointsPerSecondSaved": 0.5,
              "maximumPoints": 20
            },
            {
              "type": "PRECISION",
              "metric": "ACCURACY",
              "maximumPoints": 30
            },
            {
              "type": "JUDGES",
              "criterion": "CREATIVITY",
              "weight": 2
            },
            {
              "type": "JUDGES",
              "criterion": "EXECUTION",
              "weight": 1.5
            }
          ],
          "penalties": [
            {
              "code": "RESTART",
              "description": "manual restart",
              "pointsDeducted": 3
            }
          ],
          "maximumAttempts": 2,
          "bestRounds": null,
          "bonusCap": null,
          "mixedSources": true
        }
      ],
      "eligibility": [
        {
          "type": "AGE_RANGE"
        },
        {
          "type": "TEAM_COMPOSITION",
          "minimumCompetitors": 2,
          "maximumCompetitors": 4,
          "coachRequiredUnderAge": 18
        },
        {
          "type": "ROBOT_CLASS"
        },
        {
          "type": "ROBOT_SPECIFICATION",
          "maximumWeightKg": 3,
          "maximumDimensionsMm": {
            "length": 200,
            "width": 200,
            "height": 200
          }
        },
        {
          "type": "REQUIRED_DOCUMENTS",
          "documents": [
            "PARENTAL_CONSENT",
            "TECHNICAL_SHEET"
          ]
        }
      ],
      "attemptAggregation": {
        "type": "BEST_ATTEMPT"
      },
      "tiebreaks": [
        {
          "type": "HIGHEST_SINGLE_RUN"
        },
        {
          "type": "FEWEST_PENALTIES"
        },
        {
          "type": "FASTEST_METRIC",
          "metric": "TIME"
        }
      ],
      "appealWindowMinutes": 60
    },
  },
];

export function presetText(id: string): string {
  const preset = RULEBOOK_PRESETS.find((candidate) => candidate.id === id) ?? RULEBOOK_PRESETS[0];
  return JSON.stringify(preset.rulebook, null, 2);
}
