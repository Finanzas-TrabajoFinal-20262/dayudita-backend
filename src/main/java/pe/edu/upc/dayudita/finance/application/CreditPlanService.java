package pe.edu.upc.dayudita.finance.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.dayudita.clients.domain.model.ClientAccount;
import pe.edu.upc.dayudita.finance.domain.model.*;
import pe.edu.upc.dayudita.finance.domain.repository.CreditPlanRepository;
import pe.edu.upc.dayudita.finance.domain.repository.InstallmentRepository;
import pe.edu.upc.dayudita.finance.domain.repository.PaymentRepository;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.CreditPlanResponse;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.InstallmentDisplayStatus;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.InstallmentResponse;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.PaymentResponse;
import pe.edu.upc.dayudita.sales.domain.model.Purchase;
import pe.edu.upc.dayudita.sales.domain.model.PurchaseStatus;
import pe.edu.upc.dayudita.sales.interfaces.rest.dto.PurchaseItemResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class CreditPlanService {

    public static final int PAYMENT_PERIOD_DAYS = FinancialCalculator.PAYMENT_PERIOD_DAYS;
    public static final int MAX_PERIODOS_GRACIA = 3;
    private static final int MONTHS_PER_PERIOD = 15;
    private static final int DAYS_PER_MONTH = 30;

    private final CreditPlanRepository creditPlanRepository;
    private final InstallmentRepository installmentRepository;
    private final PaymentRepository paymentRepository;
    private final FinancialCalculator financialCalculator;
    private final FinancialDateService financialDateService;

    public CreditPlanService(
            CreditPlanRepository creditPlanRepository,
            InstallmentRepository installmentRepository,
            PaymentRepository paymentRepository,
            FinancialCalculator financialCalculator,
            FinancialDateService financialDateService
    ){
        this.creditPlanRepository = creditPlanRepository;
        this.installmentRepository = installmentRepository;
        this.paymentRepository = paymentRepository;
        this.financialCalculator = financialCalculator;
        this.financialDateService = financialDateService;
    }

    public int totalInstallments(int numeroMeses){
        return numeroMeses * DAYS_PER_MONTH / MONTHS_PER_PERIOD;
    }

    @Transactional
    public CreditPlan createPlan(Purchase purchase, BigDecimal ventaPrecio, CreditTerms terms){
        CreditPlan plan = new CreditPlan();
        plan.setPurchase(purchase);

        Schedule schedule = applySchedule(plan, ventaPrecio, terms, purchase.getPurchaseDate());
        plan = creditPlanRepository.save(plan);
        saveInstallments(plan, schedule);

        return plan;
    }

    @Transactional
    public RegenerationResult regeneratePlan(CreditPlan plan, BigDecimal ventaPrecio, CreditTerms terms){
        List<Installment> previousInstallments = installmentRepository.findCurrentByCreditPlanId(plan.getId());

        List<Payment> previousPayments = paymentRepository
                .findByInstallment_CreditPlan_IdOrderByPaymentDateAscIdAsc(plan.getId())
                .stream()
                .filter(payment -> !payment.getInstallment().isArchived())
                .toList();

        BigDecimal montoPagadoPrevio = financialCalculator.money(
                previousPayments.stream()
                        .map(Payment::getTotalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        for(Installment installment : previousInstallments){
            installment.setArchived(true);
            installmentRepository.save(installment);
        }

        Schedule schedule = applySchedule(plan, ventaPrecio, terms, plan.getPurchase().getPurchaseDate());
        plan.setStatus(CreditPlanStatus.ACTIVE);
        plan.setUpdatedAt(LocalDateTime.now());
        plan.getPurchase().setStatus(PurchaseStatus.FINANCED);
        plan = creditPlanRepository.save(plan);
        saveInstallments(plan, schedule);

        return new RegenerationResult(plan, montoPagadoPrevio, previousPayments.size());
    }

    @Transactional
    public void deletePlan(CreditPlan plan){
        paymentRepository.deleteAllByCreditPlanId(plan.getId());
        installmentRepository.deleteAllByCreditPlanId(plan.getId());
        creditPlanRepository.delete(plan);
    }

    public CreditPlanResponse simulate(BigDecimal ventaPrecio, CreditTerms terms, LocalDate purchaseDate){
        int n = totalInstallments(terms.numeroMeses());
        BigDecimal principal = financialCalculator.money(ventaPrecio);

        Schedule schedule = buildSchedule(
                principal,
                terms.teaPactada(),
                n,
                terms.periodosGracia(),
                purchaseDate
        );

        List<InstallmentResponse> installments = new ArrayList<>();
        for(Row row : schedule.rows()){
            installments.add(new InstallmentResponse(
                    null,
                    row.k(),
                    row.type(),
                    row.dueDate(),
                    row.rate(),
                    row.days(),
                    row.openingBalance(),
                    row.interest(),
                    row.payment(),
                    row.amortization(),
                    row.remainingBalance(),
                    0,
                    BigDecimal.ZERO.setScale(9),
                    financialCalculator.money(BigDecimal.ZERO),
                    financialCalculator.money(row.payment()),
                    row.type() == GraceType.TOTAL ? InstallmentDisplayStatus.GRACE_TOTAL : InstallmentDisplayStatus.PENDING,
                    null,
                    null
            ));
        }

        BigDecimal totalAPagar = financialCalculator.money(schedule.totalCuotas());

        return new CreditPlanResponse(
                null,
                null,
                null,
                null,
                purchaseDate,
                principal,
                principal,
                terms.teaPactada(),
                schedule.teq(),
                terms.teaMoratoriaPactada(),
                PAYMENT_PERIOD_DAYS,
                terms.numeroMeses(),
                n,
                terms.periodosGracia(),
                schedule.diasGraciaTotal(),
                schedule.totalIntereses(),
                schedule.interesCapitalizado(),
                schedule.totalAmortizacion(),
                schedule.totalCuotas(),
                totalAPagar,
                financialCalculator.money(BigDecimal.ZERO),
                totalAPagar,
                null,
                List.of(),
                installments,
                financialCalculator.money(BigDecimal.ZERO),
                List.of()
        );
    }

    public CreditPlanResponse toCreditPlanResponse(CreditPlan plan){
        Purchase purchase = plan.getPurchase();
        ClientAccount account = purchase.getClientAccount();
        LocalDate today = LocalDate.now();

        List<InstallmentResponse> installmentResponses = new ArrayList<>();
        BigDecimal totalInteresMoratorio = BigDecimal.ZERO;

        for(Installment installment : getInstallments(plan.getId())){
            MoratoryBreakdown breakdown = computeMoratoryBreakdown(
                    installment,
                    plan.getTeaMoratoriaPactada(),
                    today
            );

            installmentResponses.add(new InstallmentResponse(
                    installment.getId(),
                    installment.getInstallmentNumber(),
                    installment.getType(),
                    installment.getDueDate(),
                    plan.getTeq(),
                    PAYMENT_PERIOD_DAYS,
                    installment.getOpeningBalance(),
                    installment.getInterest(),
                    installment.getAmount(),
                    installment.getAmortization(),
                    installment.getRemainingBalance(),
                    breakdown.diasMora(),
                    breakdown.tasaMoratoriaTramo(),
                    breakdown.interesMoratorio(),
                    breakdown.pagoTotal(),
                    resolveDisplayStatus(installment, today),
                    installment.getPaymentDate(),
                    installment.getType() == GraceType.TOTAL
                            ? null
                            : (installment.getPaymentDate() != null ? installment.getPaymentDate() : today)
            ));

            totalInteresMoratorio = totalInteresMoratorio.add(breakdown.interesMoratorio());
        }

        totalInteresMoratorio = financialCalculator.money(totalInteresMoratorio);

        List<PaymentResponse> historicalPayments = paymentRepository
                .findByInstallment_CreditPlan_IdOrderByPaymentDateAscIdAsc(plan.getId())
                .stream()
                .filter(payment -> payment.getInstallment().isArchived())
                .map(CreditPlanService::toPaymentResponse)
                .toList();

        BigDecimal montoPagadoHistorico = financialCalculator.money(
                historicalPayments.stream()
                        .map(PaymentResponse::totalAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        List<PurchaseItemResponse> items = purchase.getDetails() == null
                ? List.of()
                : purchase.getDetails().stream()
                .map(detail -> new PurchaseItemResponse(
                        detail.getProduct().getId(),
                        detail.getProduct().getName(),
                        detail.getQuantity(),
                        detail.getUnitPrice(),
                        detail.getSubtotal()
                ))
                .toList();

        return new CreditPlanResponse(
                plan.getId(),
                purchase.getId(),
                account.getClient().getId(),
                account.getStore().getId(),
                purchase.getPurchaseDate(),
                plan.getVentaPrecio(),
                plan.getPrincipal(),
                plan.getTeaPactada(),
                plan.getTeq(),
                plan.getTeaMoratoriaPactada(),
                PAYMENT_PERIOD_DAYS,
                plan.getNumeroMeses(),
                plan.getInstallmentCount(),
                plan.resolvePeriodosGracia(),
                plan.getDiasGraciaTotal(),
                plan.getTotalIntereses(),
                plan.getInteresCapitalizado(),
                plan.getTotalAmortizacion(),
                plan.getTotalCuotas(),
                plan.getTotalAPagar(),
                totalInteresMoratorio,
                financialCalculator.money(plan.getTotalAPagar().add(totalInteresMoratorio)),
                plan.getStatus(),
                items,
                installmentResponses,
                montoPagadoHistorico,
                historicalPayments
        );
    }

    public static PaymentResponse toPaymentResponse(Payment payment){
        Installment installment = payment.getInstallment();
        return new PaymentResponse(
                payment.getId(),
                installment.getId(),
                installment.getInstallmentNumber(),
                installment.getCreditPlan() != null ? installment.getCreditPlan().getId() : null,
                payment.getPaymentDate(),
                payment.getTeaMoratoriaPactada(),
                payment.getDiasMora(),
                payment.getAmortizacion(),
                payment.getInteres(),
                payment.getInteresMoratorio(),
                payment.getTotalAmount(),
                installment.isArchived()
        );
    }

    /**
     * Mora y pago exacto de una cuota evaluados a una fecha dada (la fecha
     * de pago real si se esta registrando un pago, o hoy si solo se esta
     * consultando el cronograma).
     */
    public MoratoryBreakdown computeMoratoryBreakdown(
            Installment installment,
            BigDecimal teaMoratoriaPactada,
            LocalDate asOfDate
    ){
        int diasMora = resolveOverdueDays(installment, asOfDate);
        BigDecimal tasaMoratoriaTramo = financialCalculator.moratoryTrancheRate(teaMoratoriaPactada, diasMora);
        BigDecimal interesMoratorio = financialCalculator.moratoryInterest(installment.getAmount(), tasaMoratoriaTramo);
        BigDecimal pagoTotal = financialCalculator.totalPayment(installment.getAmount(), interesMoratorio);

        return new MoratoryBreakdown(
                diasMora,
                tasaMoratoriaTramo,
                financialCalculator.money(interesMoratorio),
                financialCalculator.money(pagoTotal)
        );
    }

    private int resolveOverdueDays(Installment installment, LocalDate asOfDate){
        if(installment.getType() == GraceType.TOTAL){
            return 0;
        }

        LocalDate referenceDate = installment.getPaymentDate() != null ? installment.getPaymentDate() : asOfDate;

        if(!referenceDate.isAfter(installment.getDueDate())){
            return 0;
        }

        return financialDateService.commercialDaysBetween(installment.getDueDate(), referenceDate);
    }

    private InstallmentDisplayStatus resolveDisplayStatus(Installment installment, LocalDate today){
        if(installment.getType() == GraceType.TOTAL){
            return InstallmentDisplayStatus.GRACE_TOTAL;
        }

        if(installment.getStatus() == InstallmentStatus.PAID){
            return InstallmentDisplayStatus.PAID;
        }

        if(installment.getDueDate().isBefore(today)){
            return InstallmentDisplayStatus.OVERDUE;
        }

        return InstallmentDisplayStatus.PENDING;
    }

    private Schedule applySchedule(CreditPlan plan, BigDecimal ventaPrecio, CreditTerms terms, LocalDate purchaseDate){
        int n = totalInstallments(terms.numeroMeses());
        BigDecimal principal = financialCalculator.money(ventaPrecio);

        Schedule schedule = buildSchedule(
                principal,
                terms.teaPactada(),
                n,
                terms.periodosGracia(),
                purchaseDate
        );

        plan.setVentaPrecio(principal);
        plan.setPorcentajeCuotaInicial(BigDecimal.ZERO);
        plan.setCuotaInicial(financialCalculator.money(BigDecimal.ZERO));
        plan.setPrincipal(principal);
        plan.setTeaPactada(terms.teaPactada());
        plan.setTeq(schedule.teq());
        plan.setTeaMoratoriaPactada(terms.teaMoratoriaPactada());
        plan.setNumeroMeses(terms.numeroMeses());
        plan.setInstallmentCount(n);
        plan.setPeriodosGracia(terms.periodosGracia());
        plan.setDiasGraciaTotal(schedule.diasGraciaTotal());
        plan.setTotalIntereses(schedule.totalIntereses());
        plan.setInteresCapitalizado(schedule.interesCapitalizado());
        plan.setTotalAmortizacion(schedule.totalAmortizacion());
        plan.setTotalCuotas(schedule.totalCuotas());
        plan.setTotalAPagar(financialCalculator.money(schedule.totalCuotas()));

        return schedule;
    }

    private void saveInstallments(CreditPlan plan, Schedule schedule){
        for(Row row : schedule.rows()){
            Installment installment = new Installment();
            installment.setCreditPlan(plan);
            installment.setInstallmentNumber(row.k());
            installment.setType(row.type());
            installment.setDueDate(row.dueDate());
            installment.setOpeningBalance(row.openingBalance());
            installment.setInterest(row.interest());
            installment.setAmount(row.payment());
            installment.setAmortization(row.amortization());
            installment.setRemainingBalance(row.remainingBalance());
            installment.setArchived(false);
            installment.setStatus(row.type() == GraceType.TOTAL ? InstallmentStatus.PAID : InstallmentStatus.PENDING);
            installmentRepository.save(installment);
        }
    }

    /**
     * Arma el cronograma de exactamente {@code n} periodos de 15 dias
     * comerciales contados desde la fecha de compra: el periodo j vence en
     * compra + 15 x j. Los primeros {@code periodosGracia} periodos (0 a 3,
     * consecutivos e incluidos en n) son de gracia total (tipo T):
     * I = SI x TEQ, cuota 0, amortizacion 0 y el interes se capitaliza
     * (SF = SI + I), sin requerir pago. Los periodos siguientes, hasta el n,
     * son cuotas estandar (tipo S) del metodo frances, calculadas sobre los
     * periodos que quedan: R = SI x TEQ(1+TEQ)^m / ((1+TEQ)^m - 1), con
     * m = n - j + 1. Ejemplo: n = 8 con 3 de gracia = 3 filas T y 5 cuotas S.
     */
    private Schedule buildSchedule(
            BigDecimal principal,
            BigDecimal teaPactada,
            int n,
            int periodosGracia,
            LocalDate purchaseDate
    ){
        if(periodosGracia < 0 || periodosGracia > MAX_PERIODOS_GRACIA){
            throw new IllegalArgumentException("Los periodos de gracia deben estar entre 0 y " + MAX_PERIODOS_GRACIA);
        }

        if(periodosGracia >= n){
            throw new IllegalArgumentException(
                    "Los periodos de gracia deben ser menores que el total de periodos del credito"
            );
        }

        BigDecimal teq = financialCalculator.periodicRate(teaPactada);

        List<Row> rows = new ArrayList<>();
        BigDecimal totalIntereses = BigDecimal.ZERO;
        BigDecimal interesCapitalizado = BigDecimal.ZERO;
        BigDecimal totalAmortizacion = BigDecimal.ZERO;
        BigDecimal totalCuotas = BigDecimal.ZERO;

        BigDecimal balance = principal;

        for(int j = 1; j <= n; j++){
            BigDecimal openingBalance = balance;
            LocalDate dueDate = financialDateService.dueDateForPeriod(purchaseDate, j);
            BigDecimal interest = financialCalculator.installmentInterest(openingBalance, teq);

            if(j <= periodosGracia){
                BigDecimal remainingBalance = openingBalance.add(interest);

                rows.add(new Row(
                        j,
                        GraceType.TOTAL,
                        dueDate,
                        teq,
                        PAYMENT_PERIOD_DAYS,
                        financialCalculator.money(openingBalance),
                        financialCalculator.money(interest),
                        financialCalculator.money(BigDecimal.ZERO),
                        financialCalculator.money(BigDecimal.ZERO),
                        financialCalculator.money(remainingBalance)
                ));

                totalIntereses = totalIntereses.add(interest);
                interesCapitalizado = interesCapitalizado.add(interest);
                balance = remainingBalance;
                continue;
            }

            BigDecimal payment = financialCalculator.frenchInstallmentPayment(openingBalance, teq, n - j + 1);
            BigDecimal amortization = financialCalculator.amortization(payment, interest);
            BigDecimal remainingBalance = openingBalance.subtract(amortization);

            rows.add(new Row(
                    j,
                    GraceType.STANDARD,
                    dueDate,
                    teq,
                    PAYMENT_PERIOD_DAYS,
                    financialCalculator.money(openingBalance),
                    financialCalculator.money(interest),
                    financialCalculator.money(payment),
                    financialCalculator.money(amortization),
                    financialCalculator.money(remainingBalance)
            ));

            totalIntereses = totalIntereses.add(interest);
            totalAmortizacion = totalAmortizacion.add(amortization);
            totalCuotas = totalCuotas.add(payment);

            balance = remainingBalance;
        }

        return new Schedule(
                teq,
                rows,
                periodosGracia * PAYMENT_PERIOD_DAYS,
                financialCalculator.money(totalIntereses),
                financialCalculator.money(interesCapitalizado),
                financialCalculator.money(totalAmortizacion),
                financialCalculator.money(totalCuotas)
        );
    }

    public List<CreditPlan> getPlansByClient(Long clientId){
        return creditPlanRepository.findByPurchase_ClientAccount_Client_IdOrderByCreatedAtDesc(clientId);
    }

    public CreditPlan getPlan(Long planId){
        return creditPlanRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("El credito no existe"));
    }

    public CreditPlan getPlanByPurchase(Long purchaseId){
        return creditPlanRepository.findByPurchase_Id(purchaseId)
                .orElseThrow(() -> new IllegalArgumentException("La compra no tiene un plan de cuotas"));
    }

    public List<Installment> getInstallments(Long creditPlanId){
        return installmentRepository.findCurrentByCreditPlanId(creditPlanId);
    }

    public boolean hasActivePlanForClient(Long clientId){
        return creditPlanRepository
                .findFirstByPurchase_ClientAccount_Client_IdAndStatus(clientId, CreditPlanStatus.ACTIVE)
                .isPresent();
    }

    public boolean hasOtherActivePlanForClient(Long clientId, Long excludedPlanId){
        return creditPlanRepository.existsByPurchase_ClientAccount_Client_IdAndStatusAndIdNot(
                clientId,
                CreditPlanStatus.ACTIVE,
                excludedPlanId
        );
    }

    private record Row(
            int k,
            GraceType type,
            LocalDate dueDate,
            BigDecimal rate,
            int days,
            BigDecimal openingBalance,
            BigDecimal interest,
            BigDecimal payment,
            BigDecimal amortization,
            BigDecimal remainingBalance
    ) {
    }

    private record Schedule(
            BigDecimal teq,
            List<Row> rows,
            int diasGraciaTotal,
            BigDecimal totalIntereses,
            BigDecimal interesCapitalizado,
            BigDecimal totalAmortizacion,
            BigDecimal totalCuotas
    ) {
    }

    public record MoratoryBreakdown(
            int diasMora,
            BigDecimal tasaMoratoriaTramo,
            BigDecimal interesMoratorio,
            BigDecimal pagoTotal
    ) {
    }

    public record RegenerationResult(
            CreditPlan plan,
            BigDecimal montoPagadoPrevio,
            int pagosPrevios
    ) {
    }
}
