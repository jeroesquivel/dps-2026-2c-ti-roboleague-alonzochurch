package com.dps.roboleague.adapter.in.web;

import com.dps.roboleague.domain.shared.DateRange;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reparte períodos de dos días, separados entre sí, para las competencias de los tests de API. El
 * contexto de Spring es compartido entre clases de test y las temporadas no pueden superponerse.
 * Las fechas quedan en 2026 para que las edades de los integrantes no cambien de categoría.
 */
final class Editions {

    private static final LocalDate FIRST_DAY = LocalDate.of(2026, 3, 1);
    private static final AtomicInteger NEXT = new AtomicInteger();

    private Editions() {
    }

    static DateRange nextPeriod() {
        LocalDate start = FIRST_DAY.plusDays(3L * NEXT.getAndIncrement());
        return DateRange.of(start, start.plusDays(1));
    }
}
