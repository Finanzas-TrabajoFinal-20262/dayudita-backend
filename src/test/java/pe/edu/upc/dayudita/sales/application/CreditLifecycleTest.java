package pe.edu.upc.dayudita.sales.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.upc.dayudita.clients.domain.model.Client;
import pe.edu.upc.dayudita.clients.domain.model.ClientAccount;
import pe.edu.upc.dayudita.clients.domain.repository.ClientAccountRepository;
import pe.edu.upc.dayudita.finance.application.*;
import pe.edu.upc.dayudita.finance.domain.model.*;
import pe.edu.upc.dayudita.finance.domain.repository.CreditPlanRepository;
import pe.edu.upc.dayudita.finance.domain.repository.InstallmentRepository;
import pe.edu.upc.dayudita.finance.domain.repository.PaymentRepository;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.CreditPlanResponse;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.InstallmentResponse;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.UpdateCreditPlanResponse;
import pe.edu.upc.dayudita.iam.application.CurrentUserService;
import pe.edu.upc.dayudita.iam.security.ApplicationUserDetails;
import pe.edu.upc.dayudita.products.domain.model.Product;
import pe.edu.upc.dayudita.products.domain.repository.ProductRepository;
import pe.edu.upc.dayudita.sales.domain.model.Purchase;
import pe.edu.upc.dayudita.sales.domain.model.PurchasePaymentMode;
import pe.edu.upc.dayudita.sales.domain.repository.PurchaseRepository;
import pe.edu.upc.dayudita.sales.interfaces.rest.dto.CreatePurchaseRequest;
import pe.edu.upc.dayudita.sales.interfaces.rest.dto.PurchaseItemRequest;
import pe.edu.upc.dayudita.sales.interfaces.rest.dto.UpdateCreditRequest;
import pe.edu.upc.dayudita.stores.domain.model.Store;
import pe.edu.upc.dayudita.stores.domain.repository.StoreRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CreditLifecycleTest {

    private static final LocalDate PURCHASE_DATE = LocalDate.of(2026, 10, 6);

    private final AtomicLong ids = new AtomicLong(100);
    private final List<CreditPlan> plans = new ArrayList<>();
    private final List<Installment> installments = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();
    private final List<Purchase> purchases = new ArrayList<>();
    private final Map<Long, Product> products = new HashMap<>();

    private Store store;
    private Store otherStore;
    private ClientAccount account;
    private FinancialConfiguration policy;
    private PurchaseService purchaseService;
    private PaymentService paymentService;
    private CreditPlanService creditPlanService;

    @BeforeEach
    void setUp(){
        store = store(1L);
        otherStore = store(2L);

        Client client = new Client();
        client.setId(10L);
        client.setFirstName("Ana");

        account = new ClientAccount();
        account.setId(20L);
        account.setClient(client);
        account.setStore(store);
        account.setTeaPactada(new BigDecimal("0.15"));
        account.setTeaMoratoriaPactada(new BigDecimal("0.12"));
        account.setLimiteCredito(new BigDecimal("1000.00"));
        account.setPlazoMaximoMeses(4);
        account.setDiaCorte(15);

        policy = new FinancialConfiguration();
        policy.setStore(store);
        policy.setCapitalMinimo(new BigDecimal("200.00"));
        policy.setCapitalMaximo(new BigDecimal("500.00"));
        policy.setTeaMinima(new BigDecimal("0.15"));
        policy.setTeaMaxima(new BigDecimal("0.25"));
        policy.setPlazoMaximoMeses(4);
        policy.setOtorgaGracia(true);

        product(1L, "Piñata unicornio", "85.00", "95.00", store);
        product(2L, "Pack de 12 peluches", "118.00", "132.00", store);
        product(3L, "Estante para postres", "135.00", "150.00", store);

        CreditPlanRepository planRepository = mock(CreditPlanRepository.class);
        when(planRepository.save(any())).thenAnswer(invocation -> {
            CreditPlan plan = invocation.getArgument(0);
            if(plan.getId() == null) plan.setId(ids.incrementAndGet());
            if(!plans.contains(plan)) plans.add(plan);
            return plan;
        });
        when(planRepository.findById(anyLong())).thenAnswer(invocation ->
                plans.stream().filter(plan -> plan.getId().equals(invocation.getArgument(0))).findFirst());
        when(planRepository.findFirstByPurchase_ClientAccount_Client_IdAndStatus(anyLong(), any())).thenAnswer(invocation ->
                plans.stream().filter(plan -> plan.getPurchase().getClientAccount().getClient().getId().equals(invocation.getArgument(0))
                        && plan.getStatus() == invocation.getArgument(1)).findFirst());
        when(planRepository.existsByPurchase_ClientAccount_Client_IdAndStatusAndIdNot(anyLong(), any(), anyLong())).thenAnswer(invocation ->
                plans.stream().anyMatch(plan -> plan.getPurchase().getClientAccount().getClient().getId().equals(invocation.getArgument(0))
                        && plan.getStatus() == invocation.getArgument(1)
                        && !plan.getId().equals(invocation.getArgument(2))));
        org.mockito.Mockito.doAnswer(invocation -> plans.remove((CreditPlan) invocation.getArgument(0)))
                .when(planRepository).delete(any());

        InstallmentRepository installmentRepository = mock(InstallmentRepository.class);
        when(installmentRepository.save(any())).thenAnswer(invocation -> {
            Installment installment = invocation.getArgument(0);
            if(installment.getId() == null) installment.setId(ids.incrementAndGet());
            if(!installments.contains(installment)) installments.add(installment);
            return installment;
        });
        when(installmentRepository.findCurrentByCreditPlanId(anyLong())).thenAnswer(invocation ->
                currentInstallments(invocation.getArgument(0)));
        when(installmentRepository.findCurrentByCreditPlanIdAndInstallmentNumber(anyLong(), any())).thenAnswer(invocation ->
                currentInstallments(invocation.getArgument(0)).stream()
                        .filter(installment -> installment.getInstallmentNumber().equals(invocation.getArgument(1)))
                        .findFirst());
        when(installmentRepository.existsCurrentByCreditPlanIdAndStatus(anyLong(), any())).thenAnswer(invocation ->
                currentInstallments(invocation.getArgument(0)).stream()
                        .anyMatch(installment -> installment.getStatus() == invocation.getArgument(1)));
        when(installmentRepository.findByIdForUpdate(anyLong())).thenAnswer(invocation ->
                installments.stream().filter(installment -> installment.getId().equals(invocation.getArgument(0))).findFirst());
        when(installmentRepository.deleteAllByCreditPlanId(anyLong())).thenAnswer(invocation -> {
            int before = installments.size();
            installments.removeIf(installment -> installment.getCreditPlan().getId().equals(invocation.getArgument(0)));
            return before - installments.size();
        });

        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        when(paymentRepository.save(any())).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if(payment.getId() == null) payment.setId(ids.incrementAndGet());
            if(!payments.contains(payment)) payments.add(payment);
            return payment;
        });
        when(paymentRepository.findByInstallment_CreditPlan_IdOrderByPaymentDateAscIdAsc(anyLong())).thenAnswer(invocation ->
                payments.stream().filter(payment -> payment.getInstallment().getCreditPlan().getId().equals(invocation.getArgument(0))).toList());
        when(paymentRepository.existsByInstallment_Id(anyLong())).thenAnswer(invocation ->
                payments.stream().anyMatch(payment -> payment.getInstallment().getId().equals(invocation.getArgument(0))));
        when(paymentRepository.deleteAllByCreditPlanId(anyLong())).thenAnswer(invocation -> {
            int before = payments.size();
            payments.removeIf(payment -> payment.getInstallment().getCreditPlan().getId().equals(invocation.getArgument(0)));
            return before - payments.size();
        });

        PurchaseRepository purchaseRepository = mock(PurchaseRepository.class);
        when(purchaseRepository.save(any())).thenAnswer(invocation -> {
            Purchase purchase = invocation.getArgument(0);
            if(purchase.getId() == null) purchase.setId(ids.incrementAndGet());
            if(!purchases.contains(purchase)) purchases.add(purchase);
            return purchase;
        });
        org.mockito.Mockito.doAnswer(invocation -> purchases.remove((Purchase) invocation.getArgument(0)))
                .when(purchaseRepository).delete(any());

        ProductRepository productRepository = mock(ProductRepository.class);
        when(productRepository.findByIdAndStore_Id(anyLong(), anyLong())).thenAnswer(invocation -> {
            Product product = products.get((Long) invocation.getArgument(0));
            return product != null && product.getStore().getId().equals(invocation.getArgument(1))
                    ? Optional.of(product) : Optional.empty();
        });

        ClientAccountRepository accountRepository = mock(ClientAccountRepository.class);
        when(accountRepository.findByClient_IdAndStore_Id(anyLong(), anyLong())).thenAnswer(invocation ->
                account.getClient().getId().equals(invocation.getArgument(0)) && store.getId().equals(invocation.getArgument(1))
                        ? Optional.of(account) : Optional.empty());

        FinancialConfigurationService configurationService = mock(FinancialConfigurationService.class);
        when(configurationService.getConfiguration(anyLong())).thenReturn(policy);

        StoreRepository storeRepository = mock(StoreRepository.class);
        when(storeRepository.findById(1L)).thenReturn(Optional.of(store));
        when(storeRepository.findById(2L)).thenReturn(Optional.of(otherStore));
        CurrentUserService currentUserService = new CurrentUserService(storeRepository);

        creditPlanService = new CreditPlanService(
                planRepository, installmentRepository, paymentRepository,
                new FinancialCalculator(), new FinancialDateService()
        );
        purchaseService = new PurchaseService(
                purchaseRepository, productRepository, accountRepository,
                configurationService, creditPlanService, currentUserService
        );
        paymentService = new PaymentService(
                planRepository, installmentRepository, paymentRepository,
                purchaseRepository, creditPlanService, currentUserService
        );

        loginAsAdminOf(1L);
    }

    @AfterEach
    void clearSecurity(){
        SecurityContextHolder.clearContext();
    }

    @Test
    void newCredit_financesThe100PercentWithItsOwnRates(){
        CreditPlan plan = createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 8, 2);

        assertEquals(new BigDecimal("322.00"), plan.getPrincipal());
        assertEquals(new BigDecimal("322.00"), plan.getVentaPrecio());
        assertEquals(0, BigDecimal.ZERO.compareTo(plan.getCuotaInicial()));
        assertEquals(0, BigDecimal.ZERO.compareTo(plan.getPorcentajeCuotaInicial()));
        assertEquals(new BigDecimal("0.20"), plan.getTeaPactada());
        assertEquals(new BigDecimal("0.40"), plan.getTeaMoratoriaPactada());
        assertEquals(2, plan.getPeriodosGracia());
        assertEquals(8, currentInstallments(plan.getId()).size());

        CreditPlanResponse response = creditPlanService.toCreditPlanResponse(plan);
        assertEquals(new BigDecimal("322.00"), response.principal());
        assertEquals(response.totalCuotas(), response.totalAPagar());
        assertEquals(GraceType.TOTAL, response.installments().get(0).type());
        assertEquals(GraceType.TOTAL, response.installments().get(1).type());
        assertEquals(GraceType.STANDARD, response.installments().get(2).type());
        assertEquals(2, response.items().size());
    }

    @Test
    void creditRates_doNotChangeWhenClientAccountChanges(){
        CreditPlan plan = createCredit(new BigDecimal("0.22"), new BigDecimal("0.45"), 8, 0);
        CreditPlanResponse before = creditPlanService.toCreditPlanResponse(plan);

        account.setTeaPactada(new BigDecimal("0.18"));
        account.setTeaMoratoriaPactada(new BigDecimal("0.12"));

        CreditPlanResponse after = creditPlanService.toCreditPlanResponse(plan);
        assertEquals(new BigDecimal("0.22"), after.teaPactada());
        assertEquals(new BigDecimal("0.45"), after.teaMoratoriaPactada());
        assertEquals(before.teq(), after.teq());

        Installment first = currentInstallments(plan.getId()).get(0);
        CreditPlanService.MoratoryBreakdown breakdown = creditPlanService.computeMoratoryBreakdown(
                first, plan.getTeaMoratoriaPactada(), first.getDueDate().plusDays(10));
        assertTrue(breakdown.diasMora() > 0);
        assertEquals(new FinancialCalculator().moratoryTrancheRate(new BigDecimal("0.45"), breakdown.diasMora()), breakdown.tasaMoratoriaTramo());
    }

    @Test
    void credit_doesNotDependOnFinancialFieldsOfTheClientAccount(){
        account.setLimiteCredito(new BigDecimal("100.00"));
        account.setPlazoMaximoMeses(1);
        account.setTeaPactada(new BigDecimal("0.15"));

        CreditPlan plan = createCredit(new BigDecimal("0.24"), new BigDecimal("0.48"), 8, 3);

        assertEquals(new BigDecimal("322.00"), plan.getPrincipal());
        assertEquals(new BigDecimal("0.24"), plan.getTeaPactada());
        assertEquals(8, plan.getInstallmentCount());
        assertEquals(3, plan.getPeriodosGracia());

        policy.setPlazoMaximoMeses(2);
        purchases.clear();
        plans.clear();
        assertThrows(IllegalArgumentException.class,
                () -> createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 6, 0));
    }

    @Test
    void moratoryRate_mustBeStrictlyGreaterThanTheStoreMaximumTea(){
        // La politica de la tienda permite TEA de 15 % a 25 %.
        for(String moratoria : new String[]{"0.18", "0.20", "0.25"}){
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> createCredit(new BigDecimal("0.20"), new BigDecimal(moratoria), 8, 0),
                    "moratoria " + moratoria + " no supera el 25 %");
            assertTrue(error.getMessage().contains("TEA moratoria debe ser mayor"));
            assertTrue(error.getMessage().contains("25 %"));
        }
        assertTrue(plans.isEmpty());

        CreditPlan plan = createCredit(new BigDecimal("0.25"), new BigDecimal("0.26"), 8, 0);
        assertEquals(new BigDecimal("0.26"), plan.getTeaMoratoriaPactada());
    }

    @Test
    void moratoryRate_isComparedAgainstTheUpdatedStoreMaximumTea(){
        policy.setTeaMaxima(new BigDecimal("0.30"));
        assertThrows(IllegalArgumentException.class,
                () -> createCredit(new BigDecimal("0.20"), new BigDecimal("0.30"), 8, 0));
        assertDoesNotThrow(() -> createCredit(new BigDecimal("0.20"), new BigDecimal("0.31"), 8, 0));
    }

    @Test
    void creditTerm_acceptsAnyNumberOfBiweeklyPeriodsUpToTheStoreMaximum(){
        for(int periods = 1; periods <= 8; periods++){
            purchases.clear();
            plans.clear();
            installments.clear();
            CreditPlan plan = createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), periods, 0);
            assertEquals(periods, plan.getInstallmentCount());
            assertEquals(periods, currentInstallments(plan.getId()).size());
        }

        purchases.clear();
        plans.clear();
        assertThrows(IllegalArgumentException.class,
                () -> createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 9, 0),
                "mas de 8 periodos");
        assertThrows(IllegalArgumentException.class,
                () -> createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 0, 0),
                "al menos 1 periodo");

        policy.setPlazoMaximoMeses(3);
        assertThrows(IllegalArgumentException.class,
                () -> createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 7, 0),
                "la tienda solo permite hasta 6 periodos");
        assertDoesNotThrow(() -> createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 5, 0));
    }

    @Test
    void creditValidation_ratesAndGrace(){
        assertThrows(IllegalArgumentException.class,
                () -> createCredit(new BigDecimal("0.20"), BigDecimal.ZERO, 8, 0),
                "la moratoria debe superar el 25 %");
        assertThrows(IllegalArgumentException.class,
                () -> createCredit(new BigDecimal("0.30"), new BigDecimal("0.40"), 8, 0),
                "TEA fuera del rango 15 %-25 %");
        assertThrows(IllegalArgumentException.class,
                () -> createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 2, 2),
                "gracia debe ser menor al total de periodos");

        policy.setOtorgaGracia(false);
        assertThrows(IllegalArgumentException.class,
                () -> createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 8, 1));
        assertTrue(plans.isEmpty());
    }

    @Test
    void editCredit_recalculatesAndPersistsTheNewSchedule(){
        CreditPlan plan = createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 8, 1);

        UpdateCreditRequest edit = new UpdateCreditRequest(
                LocalDate.of(2026, 10, 20),
                new BigDecimal("0.24"),
                new BigDecimal("0.50"),
                6,
                0,
                List.of(new PurchaseItemRequest(1L, 1), new PurchaseItemRequest(3L, 2))
        );

        CreditPlanResponse simulated = purchaseService.simulateCreditUpdate(1L, plan.getId(), edit);
        UpdateCreditPlanResponse saved = purchaseService.updateCredit(1L, plan.getId(), edit);
        CreditPlanResponse reloaded = purchaseService.getCredit(1L, plan.getId());

        assertEquals(new BigDecimal("395.00"), reloaded.principal());
        assertEquals(new BigDecimal("0.24"), reloaded.teaPactada());
        assertEquals(new BigDecimal("0.50"), reloaded.teaMoratoriaPactada());
                assertEquals(6, reloaded.installmentCount());
        assertEquals(0, reloaded.periodosGracia());
        assertEquals(LocalDate.of(2026, 10, 20), reloaded.purchaseDate());
        assertEquals(6, reloaded.installments().size());
        assertTrue(reloaded.installments().stream().allMatch(row -> row.type() == GraceType.STANDARD));
        assertEquals(0, saved.pagosPrevios());
        assertEquals(new BigDecimal("0.00"), saved.montoPagadoPrevio());

        assertEquals(simulated.totalCuotas(), reloaded.totalCuotas());
        assertEquals(simulated.totalIntereses(), reloaded.totalIntereses());
        for(int index = 0; index < 6; index++){
            InstallmentResponse expected = simulated.installments().get(index);
            InstallmentResponse actual = reloaded.installments().get(index);
            assertEquals(expected.dueDate(), actual.dueDate());
            assertEquals(expected.amount(), actual.amount());
            assertEquals(expected.remainingBalance(), actual.remainingBalance());
        }
        assertEquals(new BigDecimal("0.00"), reloaded.installments().get(5).remainingBalance());
        assertEquals(2, reloaded.items().size());
    }

    @Test
    void editCredit_withPreviousPayments_keepsThemAsHistory(){
        CreditPlan plan = createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 8, 1);
        Installment second = currentInstallments(plan.getId()).get(1);
        Payment payment = paymentService.payInstallment(1L, second.getId(), second.getDueDate());

        assertThrows(IllegalArgumentException.class,
                () -> paymentService.payInstallment(1L, second.getId(), second.getDueDate()),
                "no se permiten pagos duplicados");

        UpdateCreditPlanResponse result = purchaseService.updateCredit(1L, plan.getId(), new UpdateCreditRequest(
                PURCHASE_DATE, new BigDecimal("0.20"), new BigDecimal("0.40"), 8, 2,
                List.of(new PurchaseItemRequest(1L, 2), new PurchaseItemRequest(2L, 1))
        ));

        assertEquals(1, result.pagosPrevios());
        assertEquals(payment.getTotalAmount(), result.montoPagadoPrevio());

        assertEquals(1, payments.size());
        assertTrue(payments.get(0).getInstallment().isArchived());
        assertEquals(InstallmentStatus.PAID, payments.get(0).getInstallment().getStatus());

        CreditPlanResponse response = result.plan();
        assertEquals(CreditPlanStatus.ACTIVE, response.status());
        assertTrue(response.installments().stream()
                .filter(row -> row.type() == GraceType.STANDARD)
                .noneMatch(row -> row.paymentDate() != null));
        assertEquals(1, response.historicalPayments().size());
        assertTrue(response.historicalPayments().get(0).historical());
        assertEquals(payment.getTotalAmount(), response.montoPagadoHistorico());

        Installment archivedPending = installments.stream()
                .filter(installment -> installment.isArchived() && installment.getStatus() == InstallmentStatus.PENDING)
                .findFirst().orElseThrow();
        assertThrows(IllegalArgumentException.class,
                () -> paymentService.payInstallment(1L, archivedPending.getId(), LocalDate.now()));

        Installment newThird = currentInstallments(plan.getId()).get(2);
        paymentService.payInstallment(1L, newThird.getId(), newThird.getDueDate());
        assertEquals(1, result.pagosPrevios());
        assertEquals(1, creditPlanService.toCreditPlanResponse(plan).historicalPayments().size());
    }

    @Test
    void deleteCredit_removesPlanScheduleAndPaymentsAndFreesClient(){
        CreditPlan plan = createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 8, 1);
        Installment second = currentInstallments(plan.getId()).get(1);
        paymentService.payInstallment(1L, second.getId(), second.getDueDate());
        purchaseService.updateCredit(1L, plan.getId(), new UpdateCreditRequest(
                PURCHASE_DATE, new BigDecimal("0.20"), new BigDecimal("0.40"), 8, 0,
                List.of(new PurchaseItemRequest(1L, 2), new PurchaseItemRequest(2L, 1))
        ));

        assertThrows(IllegalArgumentException.class,
                () -> createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 8, 0),
                "con un credito activo no puede pedir otro");

        purchaseService.deleteCredit(1L, plan.getId());

        assertTrue(plans.isEmpty());
        assertTrue(installments.isEmpty());
        assertTrue(payments.isEmpty());
        assertTrue(purchases.isEmpty());
        assertEquals(3, products.size(), "los productos no se borran");
        assertFalse(creditPlanService.hasActivePlanForClient(10L));

        assertDoesNotThrow(() -> createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 8, 0),
                "el cliente queda libre para otro credito");
    }

    @Test
    void storeAdmins_cannotTouchCreditsOfAnotherStore(){
        CreditPlan plan = createCredit(new BigDecimal("0.20"), new BigDecimal("0.40"), 8, 1);
        Installment second = currentInstallments(plan.getId()).get(1);
        UpdateCreditRequest edit = new UpdateCreditRequest(
                PURCHASE_DATE, new BigDecimal("0.20"), new BigDecimal("0.40"), 4, 0,
                List.of(new PurchaseItemRequest(1L, 3))
        );

        loginAsAdminOf(2L);

        assertThrows(AccessDeniedException.class, () -> purchaseService.deleteCredit(1L, plan.getId()));
        assertThrows(AccessDeniedException.class, () -> purchaseService.updateCredit(1L, plan.getId(), edit));
        assertThrows(AccessDeniedException.class, () -> purchaseService.getCredit(1L, plan.getId()));
        assertThrows(IllegalArgumentException.class, () -> purchaseService.deleteCredit(2L, plan.getId()));
        assertThrows(IllegalArgumentException.class, () -> purchaseService.updateCredit(2L, plan.getId(), edit));
        assertThrows(IllegalArgumentException.class, () -> purchaseService.simulateCreditUpdate(2L, plan.getId(), edit));
        assertThrows(IllegalArgumentException.class,
                () -> paymentService.payInstallment(2L, second.getId(), second.getDueDate()));

        assertEquals(1, plans.size());
        assertEquals(8, currentInstallments(plan.getId()).size());
        assertTrue(payments.isEmpty());
    }

    private CreditPlan createCredit(BigDecimal tea, BigDecimal moratoria, int periods, int grace){
        Purchase purchase = purchaseService.createPurchase(1L, new CreatePurchaseRequest(
                10L,
                PURCHASE_DATE,
                PurchasePaymentMode.INSTALLMENTS,
                periods,
                tea,
                moratoria,
                grace,
                List.of(new PurchaseItemRequest(1L, 2), new PurchaseItemRequest(2L, 1))
        ));
        return plans.stream().filter(plan -> plan.getPurchase() == purchase).findFirst().orElseThrow();
    }

    private List<Installment> currentInstallments(Long planId){
        return installments.stream()
                .filter(installment -> installment.getCreditPlan().getId().equals(planId) && !installment.isArchived())
                .sorted(Comparator.comparing(Installment::getInstallmentNumber))
                .toList();
    }

    private void loginAsAdminOf(Long storeId){
        ApplicationUserDetails user = new ApplicationUserDetails(
                storeId, "Admin", "Dayu", "admin" + storeId + "@dayudita.pe", "x",
                "STORE_ADMIN", storeId, "Tienda " + storeId, true
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities())
        );
    }

    private static Store store(Long id){
        Store store = new Store();
        store.setId(id);
        store.setName("Tienda " + id);
        store.setActive(true);
        return store;
    }

    private void product(Long id, String name, String cash, String credit, Store owner){
        Product product = new Product();
        product.setId(id);
        product.setStore(owner);
        product.setName(name);
        product.setCashPrice(new BigDecimal(cash));
        product.setCreditPrice(new BigDecimal(credit));
        product.setAllowsInstallments(true);
        product.setActive(true);
        products.put(id, product);
    }
}
