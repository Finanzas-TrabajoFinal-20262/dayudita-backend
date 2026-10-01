package pe.edu.upc.dayudita.finance.application;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class FinancialCalculator {

    private static final int COMMERCIAL_YEAR_DAYS = 360;
    private static final int RATE_SCALE = 12;

    public BigDecimal effectiveRateForDays(BigDecimal annualEffectiveRate, int days){
        if(days <= 0){
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }

        //First I move the TEA to the number of days that I need.
        //TEP = (1 + TEA)^(n/360) - 1
        double base = BigDecimal.ONE.add(annualEffectiveRate).doubleValue();
        double exponent = (double) days / COMMERCIAL_YEAR_DAYS;
        double result = Math.pow(base, exponent) - 1;

        return BigDecimal.valueOf(result).setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal capitalize(BigDecimal principal, BigDecimal effectiveRate){
        //Then I capitalize the original amount with the effective rate of the period.
        //FV = PV * (1 + TEP)
        BigDecimal result = principal.multiply(BigDecimal.ONE.add(effectiveRate));
        return money(result);
    }

    public BigDecimal frenchPayment(
            BigDecimal principal,
            BigDecimal periodRate,
            int installmentCount
    ){
        //For the installment plan I use the ordinary overdue French method.
        //R = C * [i(1+i)^n / ((1+i)^n - 1)]
        double c = principal.doubleValue();
        double i = periodRate.doubleValue();
        double factor = Math.pow(1 + i, installmentCount);
        double payment = c * ((i * factor) / (factor - 1));

        return money(BigDecimal.valueOf(payment));
    }

    public BigDecimal installmentInterest(BigDecimal openingBalance, BigDecimal periodRate){
        //In each installment the interest is calculated with the opening balance.
        //I = SI * TEP
        return money(openingBalance.multiply(periodRate));
    }

    public BigDecimal installmentAmortization(BigDecimal payment, BigDecimal interest){
        //After calculating the interest, the rest of the payment amortizes the capital.
        //A = R - I
        return money(payment.subtract(interest));
    }

    public BigDecimal moratoryInterest(
            BigDecimal overdueAmount,
            BigDecimal moratoryAnnualEffectiveRate,
            int overdueDays
    ){
        if(overdueDays <= 0){
            return money(BigDecimal.ZERO);
        }

        //For overdue days I first move the moratory TEA to those days and then apply it to the overdue amount.
        //TEMora(n days) = (1 + TEAMora)^(n/360) - 1
        //Mora = Overdue amount * TEMora(n days)
        BigDecimal moratoryRate = effectiveRateForDays(moratoryAnnualEffectiveRate, overdueDays);
        return money(overdueAmount.multiply(moratoryRate));
    }

    public BigDecimal money(BigDecimal value){
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
