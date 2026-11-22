package pe.edu.upc.dayudita.sales.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.upc.dayudita.clients.domain.model.ClientAccount;
import pe.edu.upc.dayudita.clients.domain.repository.ClientAccountRepository;
import pe.edu.upc.dayudita.finance.application.CreditPlanService;
import pe.edu.upc.dayudita.finance.application.CreditTerms;
import pe.edu.upc.dayudita.finance.application.FinancialConfigurationService;
import pe.edu.upc.dayudita.finance.domain.model.CreditPlan;
import pe.edu.upc.dayudita.finance.domain.model.FinancialConfiguration;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.CreditPlanResponse;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.UpdateCreditPlanResponse;
import pe.edu.upc.dayudita.iam.application.CurrentUserService;
import pe.edu.upc.dayudita.products.domain.model.Product;
import pe.edu.upc.dayudita.products.domain.repository.ProductRepository;
import pe.edu.upc.dayudita.sales.domain.model.*;
import pe.edu.upc.dayudita.sales.domain.repository.PurchaseRepository;
import pe.edu.upc.dayudita.sales.interfaces.rest.dto.CreatePurchaseRequest;
import pe.edu.upc.dayudita.sales.interfaces.rest.dto.PurchaseItemRequest;
import pe.edu.upc.dayudita.sales.interfaces.rest.dto.UpdateCreditRequest;

import java.math.BigDecimal;
import java.util.*;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;
    private final ProductRepository productRepository;
    private final ClientAccountRepository clientAccountRepository;
    private final FinancialConfigurationService financialConfigurationService;
    private final CreditPlanService creditPlanService;
    private final CurrentUserService currentUserService;

    public PurchaseService(
            PurchaseRepository purchaseRepository,
            ProductRepository productRepository,
            ClientAccountRepository clientAccountRepository,
            FinancialConfigurationService financialConfigurationService,
            CreditPlanService creditPlanService,
            CurrentUserService currentUserService
    ){
        this.purchaseRepository = purchaseRepository;
        this.productRepository = productRepository;
        this.clientAccountRepository = clientAccountRepository;
        this.financialConfigurationService = financialConfigurationService;
        this.creditPlanService = creditPlanService;
        this.currentUserService = currentUserService;
    }

    @Transactional
    public Purchase createPurchase(Long storeId, CreatePurchaseRequest request){
        currentUserService.validateStoreAdmin(storeId);

        ClientAccount account = clientAccountRepository
                .findByClient_IdAndStore_Id(request.clientId(), storeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El cliente no se encuentra asociado a esta tienda"
                ));

        if(!account.getActive()){
            throw new IllegalArgumentException("El cliente se encuentra inactivo en esta tienda");
        }

        Purchase purchase = new Purchase();
        purchase.setClientAccount(account);
        purchase.setPurchaseDate(request.purchaseDate());
        purchase.setPaymentMode(request.paymentMode());

        BigDecimal total = priceItems(storeId, request.paymentMode(), request.items(), purchase, Map.of());
        purchase.setTotal(total);

        if(request.paymentMode() == PurchasePaymentMode.CASH){
            purchase.setStatus(PurchaseStatus.PAID);
            return purchaseRepository.save(purchase);
        }

        CreditTerms terms = resolveCreditTerms(
                request.numeroMeses(),
                request.teaPactada(),
                request.teaMoratoriaPactada(),
                request.periodosGracia()
        );
        validateCreditPurchase(storeId, account, terms, total, null);

        purchase.setStatus(PurchaseStatus.FINANCED);
        purchase = purchaseRepository.save(purchase);

        creditPlanService.createPlan(purchase, total, terms);

        return purchase;
    }

    public CreditPlanResponse simulateInstallmentPurchase(Long storeId, CreatePurchaseRequest request){
        currentUserService.validateStoreAdmin(storeId);

        ClientAccount account = clientAccountRepository
                .findByClient_IdAndStore_Id(request.clientId(), storeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "El cliente no se encuentra asociado a esta tienda"
                ));

        if(!account.getActive()){
            throw new IllegalArgumentException("El cliente se encuentra inactivo en esta tienda");
        }

        BigDecimal total = priceItems(storeId, PurchasePaymentMode.INSTALLMENTS, request.items(), null, Map.of());
        CreditTerms terms = resolveCreditTerms(
                request.numeroMeses(),
                request.teaPactada(),
                request.teaMoratoriaPactada(),
                request.periodosGracia()
        );
        validateCreditPurchase(storeId, account, terms, total, null);

        return creditPlanService.simulate(total, terms, request.purchaseDate());
    }

    public CreditPlanResponse simulateCreditUpdate(Long storeId, Long planId, UpdateCreditRequest request){
        CreditPlan plan = getStorePlan(storeId, planId);
        Purchase purchase = plan.getPurchase();

        BigDecimal total = priceItems(
                storeId,
                PurchasePaymentMode.INSTALLMENTS,
                request.items(),
                null,
                originalUnitPrices(purchase)
        );
        CreditTerms terms = resolveCreditTerms(
                request.numeroMeses(),
                request.teaPactada(),
                request.teaMoratoriaPactada(),
                request.periodosGracia()
        );
        validateCreditPurchase(storeId, purchase.getClientAccount(), terms, total, plan.getId());

        return creditPlanService.simulate(total, terms, request.purchaseDate());
    }

    @Transactional
    public UpdateCreditPlanResponse updateCredit(Long storeId, Long planId, UpdateCreditRequest request){
        CreditPlan plan = getStorePlan(storeId, planId);
        Purchase purchase = plan.getPurchase();
        Map<Long, BigDecimal> originalPrices = originalUnitPrices(purchase);

        CreditTerms terms = resolveCreditTerms(
                request.numeroMeses(),
                request.teaPactada(),
                request.teaMoratoriaPactada(),
                request.periodosGracia()
        );

        BigDecimal total = priceItems(storeId, PurchasePaymentMode.INSTALLMENTS, request.items(), null, originalPrices);
        validateCreditPurchase(storeId, purchase.getClientAccount(), terms, total, plan.getId());

        purchase.getDetails().clear();
        priceItems(storeId, PurchasePaymentMode.INSTALLMENTS, request.items(), purchase, originalPrices);
        purchase.setPurchaseDate(request.purchaseDate());
        purchase.setTotal(total);
        purchaseRepository.save(purchase);

        CreditPlanService.RegenerationResult result = creditPlanService.regeneratePlan(plan, total, terms);

        return new UpdateCreditPlanResponse(
                creditPlanService.toCreditPlanResponse(result.plan()),
                result.montoPagadoPrevio(),
                result.pagosPrevios()
        );
    }

    @Transactional
    public void deleteCredit(Long storeId, Long planId){
        CreditPlan plan = getStorePlan(storeId, planId);
        Purchase purchase = plan.getPurchase();

        creditPlanService.deletePlan(plan);
        purchaseRepository.delete(purchase);
    }

    public CreditPlanResponse getCredit(Long storeId, Long planId){
        return creditPlanService.toCreditPlanResponse(getStorePlan(storeId, planId));
    }

    public List<Purchase> getPurchasesByStore(Long storeId){
        currentUserService.validateStoreAdmin(storeId);
        return purchaseRepository.findByClientAccount_Store_IdOrderByPurchaseDateDesc(storeId);
    }

    public List<Purchase> getPurchasesByClient(Long clientId){
        return purchaseRepository.findByClientAccount_Client_IdOrderByPurchaseDateDesc(clientId);
    }

    private CreditPlan getStorePlan(Long storeId, Long planId){
        currentUserService.validateStoreAdmin(storeId);

        CreditPlan plan = creditPlanService.getPlan(planId);

        if(!plan.getPurchase().getClientAccount().getStore().getId().equals(storeId)){
            throw new IllegalArgumentException("El credito no pertenece a esta tienda");
        }

        return plan;
    }

    private Map<Long, BigDecimal> originalUnitPrices(Purchase purchase){
        Map<Long, BigDecimal> prices = new HashMap<>();
        for(PurchaseDetail detail : purchase.getDetails()){
            prices.put(detail.getProduct().getId(), detail.getUnitPrice());
        }
        return prices;
    }

    private BigDecimal priceItems(
            Long storeId,
            PurchasePaymentMode paymentMode,
            List<PurchaseItemRequest> itemRequests,
            Purchase purchase,
            Map<Long, BigDecimal> originalUnitPrices
    ){
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> productIds = new HashSet<>();

        for(PurchaseItemRequest itemRequest : itemRequests){
            if(!productIds.add(itemRequest.productId())){
                throw new IllegalArgumentException("Un producto no puede repetirse dentro de la misma compra");
            }

            Product product = productRepository.findByIdAndStore_Id(itemRequest.productId(), storeId)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Uno de los productos no pertenece a esta tienda"
                    ));

            if(!product.getActive()){
                throw new IllegalArgumentException("Uno de los productos se encuentra inactivo");
            }

            if(paymentMode == PurchasePaymentMode.INSTALLMENTS && !product.getAllowsInstallments()){
                throw new IllegalArgumentException("Uno de los productos no permite pago en cuotas");
            }

            BigDecimal unitPrice;
            if(paymentMode == PurchasePaymentMode.CASH){
                unitPrice = product.getCashPrice();
            } else {
                unitPrice = originalUnitPrices.getOrDefault(product.getId(), product.getCreditPrice());
            }
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity()));

            if(purchase != null){
                PurchaseDetail detail = new PurchaseDetail();
                detail.setPurchase(purchase);
                detail.setProduct(product);
                detail.setQuantity(itemRequest.quantity());
                detail.setUnitPrice(unitPrice);
                detail.setSubtotal(subtotal);
                purchase.getDetails().add(detail);
            }

            total = total.add(subtotal);
        }

        return total.setScale(2);
    }

    private CreditTerms resolveCreditTerms(
            Integer numeroMeses,
            BigDecimal teaPactada,
            BigDecimal teaMoratoriaPactada,
            Integer periodosGracia
    ){
        if(numeroMeses == null){
            throw new IllegalArgumentException("Debe indicar el numero de meses");
        }

        if(teaPactada == null){
            throw new IllegalArgumentException("Debe indicar la TEA del credito");
        }

        if(teaMoratoriaPactada == null){
            throw new IllegalArgumentException("Debe indicar la TEA moratoria del credito");
        }

        return new CreditTerms(
                teaPactada,
                teaMoratoriaPactada,
                numeroMeses,
                periodosGracia == null ? 0 : periodosGracia
        );
    }

    private FinancialConfiguration validateCreditPurchase(
            Long storeId,
            ClientAccount account,
            CreditTerms terms,
            BigDecimal ventaPrecio,
            Long excludedPlanId
    ){
        FinancialConfiguration policy = financialConfigurationService.getConfiguration(storeId);

        BigDecimal principal = ventaPrecio;

        if(principal.compareTo(policy.getCapitalMinimo()) < 0
                || principal.compareTo(policy.getCapitalMaximo()) > 0){
            throw new IllegalArgumentException(
                    "El monto del credito se encuentra fuera del rango permitido por la tienda"
            );
        }

        BigDecimal teaPactada = terms.teaPactada();
        if(teaPactada.compareTo(BigDecimal.ZERO) <= 0
                || teaPactada.compareTo(policy.getTeaMinima()) < 0
                || teaPactada.compareTo(policy.getTeaMaxima()) > 0){
            throw new IllegalArgumentException(
                    "La TEA del credito se encuentra fuera del rango permitido por la tienda"
            );
        }

        if(terms.teaMoratoriaPactada().compareTo(BigDecimal.ZERO) <= 0){
            throw new IllegalArgumentException(
                    "La TEA moratoria debe ser mayor a 0"
            );
        }

        if(terms.numeroMeses() < 1 || terms.numeroMeses() > policy.getPlazoMaximoMeses()){
            throw new IllegalArgumentException(
                    "El numero de meses supera el plazo maximo permitido por la tienda"
            );
        }

        int totalPeriodos = creditPlanService.totalInstallments(terms.numeroMeses());

        if(terms.periodosGracia() < 0 || terms.periodosGracia() > CreditPlanService.MAX_PERIODOS_GRACIA){
            throw new IllegalArgumentException("Los periodos de gracia deben estar entre 0 y 3");
        }

        if(terms.periodosGracia() > 0 && !Boolean.TRUE.equals(policy.getOtorgaGracia())){
            throw new IllegalArgumentException("La tienda no otorga periodos de gracia");
        }

        if(terms.periodosGracia() >= totalPeriodos){
            throw new IllegalArgumentException(
                    "Los periodos de gracia deben ser menores que el total de periodos del credito"
            );
        }

        Long clientId = account.getClient().getId();
        boolean otherActivePlan = excludedPlanId == null
                ? creditPlanService.hasActivePlanForClient(clientId)
                : creditPlanService.hasOtherActivePlanForClient(clientId, excludedPlanId);

        if(otherActivePlan){
            throw new IllegalArgumentException(
                    "El cliente ya tiene un credito en cuotas activo"
            );
        }

        return policy;
    }
}
