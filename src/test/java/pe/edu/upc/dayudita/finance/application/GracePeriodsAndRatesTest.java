package pe.edu.upc.dayudita.finance.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.edu.upc.dayudita.finance.domain.model.FinancialConfiguration;
import pe.edu.upc.dayudita.finance.domain.model.GraceType;
import pe.edu.upc.dayudita.finance.domain.repository.FinancialConfigurationRepository;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.CreditPlanResponse;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.InstallmentResponse;
import pe.edu.upc.dayudita.stores.domain.model.Store;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GracePeriodsAndRatesTest {

    private static final BigDecimal CAPITAL = new BigDecimal("500.00");
    private static final BigDecimal TEA = new BigDecimal("0.20");
    private static final BigDecimal TEA_MORATORIA = new BigDecimal("0.40");
    private static final BigDecimal TEQ_20 = new BigDecimal("0.007625660");
    private static final LocalDate PURCHASE_DATE = LocalDate.of(2026, 10, 6);

    private final FinancialCalculator calculator = new FinancialCalculator();
    private final FinancialDateService dateService = new FinancialDateService();
    private final CreditPlanService service = new CreditPlanService(null, null, null, calculator, dateService);

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void totalGrace_isIncludedInTermAndCapitalizesInterest(int graceCount){
        CreditPlanResponse plan = service.simulate(CAPITAL, new CreditTerms(TEA, TEA_MORATORIA, 8, graceCount), PURCHASE_DATE);

        assertEquals(8, plan.installmentCount(), "8 periodos incluyen la gracia");
        assertEquals(8, plan.installments().size());
        assertEquals(graceCount, plan.periodosGracia());
        assertEquals(graceCount * 15, plan.diasGraciaTotal());
        assertEquals(TEQ_20, plan.teq());
        assertEquals(CAPITAL, plan.principal(), "sin cuota inicial: se financia el 100 %");

        BigDecimal balance = CAPITAL;
        BigDecimal capitalized = BigDecimal.ZERO;
        BigDecimal totalPayments = BigDecimal.ZERO;

        for(int j = 1; j <= 8; j++){
            InstallmentResponse row = plan.installments().get(j - 1);
            String label = "gracia " + graceCount + ", periodo " + j;
            BigDecimal interest = calculator.installmentInterest(balance, TEQ_20);

            assertEquals(dateService.dueDateForPeriod(PURCHASE_DATE, j), row.dueDate(), label);
            assertEquals(calculator.money(balance), row.openingBalance(), label);
            assertEquals(calculator.money(interest), row.interest(), label);

            if(j <= graceCount){
                assertEquals(GraceType.TOTAL, row.type(), label);
                assertEquals(new BigDecimal("0.00"), row.amount(), label);
                assertEquals(new BigDecimal("0.00"), row.amortization(), label);
                balance = balance.add(interest);
                capitalized = capitalized.add(interest);
            } else {
                assertEquals(GraceType.STANDARD, row.type(), label);
                BigDecimal payment = calculator.frenchInstallmentPayment(balance, TEQ_20, 8 - j + 1);
                assertEquals(calculator.money(payment), row.amount(), label);
                balance = balance.subtract(calculator.amortization(payment, interest));
                totalPayments = totalPayments.add(payment);
            }

            assertEquals(calculator.money(balance), row.remainingBalance(), label);
        }

        assertEquals(new BigDecimal("0.00"), plan.installments().get(7).remainingBalance());
        assertTrue(balance.abs().compareTo(new BigDecimal("0.0000005")) <= 0,
                "saldo final interno cero (gracia " + graceCount + "): " + balance);
        assertEquals(calculator.money(CAPITAL.add(capitalized)), plan.totalAmortizacion(),
                "amortizaciones = capital + capitalizado (gracia " + graceCount + ")");
        assertEquals(calculator.money(capitalized), plan.interesCapitalizado());
        assertEquals(calculator.money(totalPayments), plan.totalCuotas());
        assertEquals(plan.totalCuotas(), plan.totalAPagar(), "total a pagar = suma de cuotas");

        long paymentRows = plan.installments().stream().filter(row -> row.type() == GraceType.STANDARD).count();
        assertEquals(8 - graceCount, paymentRows);
    }

    @Test
    void threeGracePeriods_leaveFivePaymentsWithTheSameRecalculatedInstallment(){
        CreditPlanResponse plan = service.simulate(CAPITAL, new CreditTerms(TEA, TEA_MORATORIA, 8, 3), PURCHASE_DATE);

        BigDecimal firstPayment = plan.installments().get(3).amount();
        for(InstallmentResponse row : plan.installments().subList(3, 8)){
            assertTrue(firstPayment.subtract(row.amount()).abs().compareTo(new BigDecimal("0.01")) <= 0,
                    "cuota francesa constante en los 5 periodos de pago");
        }

        assertEquals(new BigDecimal("511.53"), plan.installments().get(2).remainingBalance());
    }

    @Test
    void graceMustBeLowerThanTotalPeriods(){
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(CAPITAL, new CreditTerms(TEA, TEA_MORATORIA, 2, 2), PURCHASE_DATE));
        assertThrows(IllegalArgumentException.class,
                () -> service.simulate(CAPITAL, new CreditTerms(TEA, TEA_MORATORIA, 8, 4), PURCHASE_DATE));
        assertDoesNotThrow(
                () -> service.simulate(CAPITAL, new CreditTerms(TEA, TEA_MORATORIA, 2, 1), PURCHASE_DATE));
    }

    @Test
    void tea20AndMoratory40_produceExpectedRates(){
        assertEquals(TEQ_20, calculator.periodicRate(TEA));

        BigDecimal installment = new BigDecimal("86.22");
        assertEquals(new BigDecimal("0.000935082"), calculator.moratoryTrancheRate(TEA_MORATORIA, 1));
        assertEquals(new BigDecimal("0.08"), calculator.money(calculator.moratoryInterest(installment, calculator.moratoryTrancheRate(TEA_MORATORIA, 1))));
        assertEquals(new BigDecimal("0.24"), calculator.money(calculator.moratoryInterest(installment, calculator.moratoryTrancheRate(TEA_MORATORIA, 3))));
        assertEquals(new BigDecimal("0.81"), calculator.money(calculator.moratoryInterest(installment, calculator.moratoryTrancheRate(TEA_MORATORIA, 10))));
    }

    @Test
    void simulationExposesTheRatesOfTheCredit(){
        CreditPlanResponse plan = service.simulate(
                CAPITAL,
                new CreditTerms(new BigDecimal("0.22"), new BigDecimal("0.45"), 4, 0),
                PURCHASE_DATE
        );

        assertEquals(new BigDecimal("0.22"), plan.teaPactada());
        assertEquals(new BigDecimal("0.45"), plan.teaMoratoriaPactada());
        assertEquals(new BigDecimal("0.008319872"), plan.teq());
        plan.installments().forEach(row -> assertEquals(new BigDecimal("0.008319872"), row.tasaPeriodo()));
    }

    @Test
    void defaultStorePolicy_allowsTeaBetween15And25Percent(){
        FinancialConfigurationRepository repository = mock(FinancialConfigurationRepository.class);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        FinancialConfiguration policy = new FinancialConfigurationService(repository)
                .createDefaultConfiguration(new Store());

        assertEquals(0, new BigDecimal("0.15").compareTo(policy.getTeaMinima()));
        assertEquals(0, new BigDecimal("0.25").compareTo(policy.getTeaMaxima()));
        assertTrue(policy.getTeaMinima().compareTo(TEA) <= 0 && policy.getTeaMaxima().compareTo(TEA) >= 0,
                "la TEA inicial de 20 % esta dentro del rango");
    }
}
