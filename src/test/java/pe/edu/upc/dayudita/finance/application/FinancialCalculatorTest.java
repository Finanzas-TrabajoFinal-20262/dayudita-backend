package pe.edu.upc.dayudita.finance.application;

import org.junit.jupiter.api.Test;

import pe.edu.upc.dayudita.finance.domain.model.GraceType;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.CreditPlanResponse;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.InstallmentResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Casos A (sin gracia), B (gracia total a la TEQ) y C (mora) del simulador
 * de Excel.
 */
class FinancialCalculatorTest {

    private final FinancialCalculator calculator = new FinancialCalculator();

    @Test
    void caseA_withoutGrace(){
        BigDecimal c = new BigDecimal("500.00");
        BigDecimal tea = new BigDecimal("0.20");
        int n = 8;

        BigDecimal teq = calculator.periodicRate(tea);
        assertEquals(new BigDecimal("0.007625660"), teq);

        BigDecimal balance = c;
        BigDecimal totalPaid = BigDecimal.ZERO;

        for(int k = 1; k <= n; k++){
            BigDecimal opening = balance;
            BigDecimal interest = calculator.installmentInterest(opening, teq);
            BigDecimal payment = calculator.frenchInstallmentPayment(opening, teq, n - k + 1);
            BigDecimal amortization = calculator.amortization(payment, interest);
            BigDecimal remaining = opening.subtract(amortization);

            assertEquals(new BigDecimal("64.66"), calculator.money(payment), "cuota k=" + k);

            totalPaid = totalPaid.add(payment);
            balance = remaining;
        }

        assertEquals(new BigDecimal("0.00"), calculator.money(balance), "saldo final k=8");
        assertEquals(new BigDecimal("517.31"), calculator.money(totalPaid), "total a pagar sin CI");
    }

    @Test
    void caseB_withTotalGrace_usesTeqAndCapitalizes(){
        CreditPlanResponse plan = simulate(LocalDate.of(2026, 10, 6), true);

        assertEquals(new BigDecimal("0.007625660"), plan.teq());
        assertEquals(9, plan.diasGraciaTotal());
        assertEquals(8, plan.installmentCount(), "cuotas S, sin la fila T");
        assertEquals(9, plan.installments().size());

        InstallmentResponse grace = plan.installments().get(0);
        assertEquals(GraceType.TOTAL, grace.type());
        assertEquals(LocalDate.of(2026, 10, 15), grace.dueDate());
        assertEquals(new BigDecimal("0.007625660"), grace.tasaPeriodo());
        assertEquals(9, grace.diasPeriodo());
        assertEquals(new BigDecimal("500.00"), grace.openingBalance());
        assertEquals(new BigDecimal("3.81"), grace.interest());
        assertEquals(
                new BigDecimal("3.8128300"),
                calculator.installmentInterest(new BigDecimal("500.00"), plan.teq()),
                "interes interno de la fila T"
        );
        assertEquals(new BigDecimal("0.00"), grace.amount());
        assertEquals(new BigDecimal("0.00"), grace.amortization());
        assertEquals(new BigDecimal("503.81"), grace.remainingBalance());

        assertEquals(
                new BigDecimal("65.1568276"),
                calculator.frenchInstallmentPayment(new BigDecimal("503.8128300"), plan.teq(), 8),
                "cuota S interna"
        );

        List<InstallmentResponse> standard = plan.installments().subList(1, 9);
        for(InstallmentResponse row : standard){
            assertEquals(GraceType.STANDARD, row.type());
            assertEquals(new BigDecimal("65.16"), row.amount(), "cuota " + row.installmentNumber());
        }
        assertEquals(LocalDate.of(2026, 10, 30), standard.get(0).dueDate());
        assertEquals(LocalDate.of(2027, 2, 15), standard.get(7).dueDate());
        assertEquals(new BigDecimal("0.00"), standard.get(7).remainingBalance(), "saldo final cuota 8");

        assertEquals(new BigDecimal("21.25"), plan.totalIntereses());
        assertEquals(new BigDecimal("3.81"), plan.interesCapitalizado());
        assertEquals(new BigDecimal("521.25"), plan.totalCuotas());
    }

    @Test
    void caseB_graceRowDoesNotDependOnGraceDays(){
        CreditPlanResponse plan = simulate(LocalDate.of(2026, 8, 25), true);

        assertEquals(20, plan.diasGraciaTotal());

        InstallmentResponse grace = plan.installments().get(0);
        assertEquals(GraceType.TOTAL, grace.type());
        assertEquals(new BigDecimal("0.007625660"), grace.tasaPeriodo());
        assertEquals(20, grace.diasPeriodo());
        assertEquals(new BigDecimal("3.81"), calculator.money(grace.interest()));
        assertEquals(new BigDecimal("503.81"), grace.remainingBalance());
    }

    @Test
    void caseA_scheduleWithoutGraceHasNoTotalGraceRow(){
        CreditPlanResponse plan = simulate(LocalDate.of(2026, 10, 15), false);

        assertEquals(0, plan.diasGraciaTotal());
        assertEquals(8, plan.installmentCount());
        assertEquals(8, plan.installments().size());
        assertEquals(GraceType.STANDARD, plan.installments().get(0).type());
        assertEquals(new BigDecimal("64.66"), calculator.money(plan.installments().get(0).amount()));
        assertEquals(new BigDecimal("517.31"), plan.totalCuotas());
    }

    private CreditPlanResponse simulate(LocalDate purchaseDate, boolean aplicaGracia){
        CreditPlanService service = new CreditPlanService(null, null, calculator, new FinancialDateService());
        return service.simulate(
                new BigDecimal("500.00"),
                BigDecimal.ZERO,
                4,
                aplicaGracia,
                new BigDecimal("0.20"),
                new BigDecimal("0.12"),
                purchaseDate,
                15
        );
    }

    @Test
    void caseC_moratoryInterestOnStandardInstallment(){
        BigDecimal moratoryTea = new BigDecimal("0.12");
        BigDecimal installmentThreePayment = new BigDecimal("86.22");

        assertEquals(
                new BigDecimal("0.03"),
                calculator.money(calculator.moratoryInterest(
                        installmentThreePayment,
                        calculator.moratoryTrancheRate(moratoryTea, 1)
                )),
                "mora a 1 dia"
        );

        assertEquals(
                new BigDecimal("0.08"),
                calculator.money(calculator.moratoryInterest(
                        installmentThreePayment,
                        calculator.moratoryTrancheRate(moratoryTea, 3)
                )),
                "mora a 3 dias"
        );

        assertEquals(
                new BigDecimal("0.27"),
                calculator.money(calculator.moratoryInterest(
                        installmentThreePayment,
                        calculator.moratoryTrancheRate(moratoryTea, 10)
                )),
                "mora a 10 dias"
        );
    }
}
