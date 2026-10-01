package pe.edu.upc.dayudita.finance.application;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Fechas sobre año comercial de 360 días (12 meses de 30 días), usando la
 * convención europea de DIAS360: solo el día 31 se ajusta a 30; los días de
 * febrero no se tocan.
 */
@Service
public class FinancialDateService {

    public int commercialDaysBetween(LocalDate startDate, LocalDate endDate){
        if(endDate.isBefore(startDate)){
            throw new IllegalArgumentException("La fecha final no puede ser anterior a la fecha inicial");
        }

        int startDay = commercialDay(startDate);
        int endDay = commercialDay(endDate);

        return (endDate.getYear() - startDate.getYear()) * 360
                + (endDate.getMonthValue() - startDate.getMonthValue()) * 30
                + (endDay - startDay);
    }

    private LocalDate dateForDay(YearMonth month, Integer contractualDay){
        int realDay = Math.min(contractualDay, month.lengthOfMonth());
        return month.atDay(realDay);
    }

    /**
     * Siguiente fecha de corte (15 o 30 de cada mes; en meses mas cortos, el
     * corte "30" cae en el ultimo dia del mes) igual o posterior a la fecha
     * dada. Si la fecha ya es una fecha de corte, se devuelve ella misma
     * (gracia de 0 dias).
     */
    public LocalDate nextCutoffOnOrAfter(LocalDate date){
        YearMonth month = YearMonth.from(date);

        if(date.getDayOfMonth() <= 15){
            return month.atDay(15);
        }

        LocalDate secondCutoff = dateForDay(month, 30);
        if(!date.isAfter(secondCutoff)){
            return secondCutoff;
        }

        return month.plusMonths(1).atDay(15);
    }

    /**
     * Fecha de corte que esta {@code steps} quincenas despues de {@code cutoff}
     * (que debe ser, a su vez, una fecha de corte: dia 15, o el dia 30/fin de
     * mes). Avanza por indice de quincena (mes*2 + mitad), no por suma de dias
     * comerciales, para no desalinearse del 15/30 cuando un corte cae en un
     * mes corto (ej. 28 de febrero).
     */
    public LocalDate cutoffAfter(LocalDate cutoff, int steps){
        boolean isSecondHalf = cutoff.getDayOfMonth() > 15;
        int monthIndex0 = cutoff.getYear() * 12 + (cutoff.getMonthValue() - 1);
        int halfIndex = isSecondHalf ? 1 : 0;

        int totalHalfSteps = monthIndex0 * 2 + halfIndex + steps;
        int targetMonthIndex0 = Math.floorDiv(totalHalfSteps, 2);
        int targetHalf = Math.floorMod(totalHalfSteps, 2);

        YearMonth targetMonth = YearMonth.of(
                Math.floorDiv(targetMonthIndex0, 12),
                Math.floorMod(targetMonthIndex0, 12) + 1
        );

        return targetHalf == 1 ? dateForDay(targetMonth, 30) : targetMonth.atDay(15);
    }

    private int commercialDay(LocalDate date){
        return date.getDayOfMonth() == 31 ? 30 : date.getDayOfMonth();
    }
}
