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

    /**
     * fechaVencimiento(k) = fechaCompra + 15*k días, contados en meses
     * comerciales de 30 días (equivalente a EDATE + ajuste quincenal).
     */
    public LocalDate dueDateForInstallment(LocalDate purchaseDate, int installmentNumber, int periodDays){
        if(installmentNumber < 0 || periodDays <= 0){
            throw new IllegalArgumentException("El periodo de pago no es valido");
        }

        int commercialIndex = purchaseDate.getYear() * 360
                + (purchaseDate.getMonthValue() - 1) * 30
                + (commercialDay(purchaseDate) - 1)
                + installmentNumber * periodDays;

        int year = Math.floorDiv(commercialIndex, 360);
        int dayOfYear = Math.floorMod(commercialIndex, 360);
        int month = dayOfYear / 30 + 1;
        int day = dayOfYear % 30 + 1;

        return dateForDay(YearMonth.of(year, month), day);
    }

    private LocalDate dateForDay(YearMonth month, Integer contractualDay){
        int realDay = Math.min(contractualDay, month.lengthOfMonth());
        return month.atDay(realDay);
    }

    private int commercialDay(LocalDate date){
        return date.getDayOfMonth() == 31 ? 30 : date.getDayOfMonth();
    }
}
