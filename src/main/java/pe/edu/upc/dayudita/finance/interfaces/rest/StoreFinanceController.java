package pe.edu.upc.dayudita.finance.interfaces.rest;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.edu.upc.dayudita.finance.application.CreditPlanService;
import pe.edu.upc.dayudita.finance.application.PaymentService;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.CreditPlanResponse;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.PaymentResponse;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.RegisterPaymentRequest;
import pe.edu.upc.dayudita.finance.interfaces.rest.dto.UpdateCreditPlanResponse;
import pe.edu.upc.dayudita.iam.application.CurrentUserService;
import pe.edu.upc.dayudita.sales.application.PurchaseService;
import pe.edu.upc.dayudita.sales.interfaces.rest.dto.UpdateCreditRequest;

import java.util.List;

@RestController
@RequestMapping("/api/stores/{storeId}/finance")
@PreAuthorize("hasRole('STORE_ADMIN')")
public class StoreFinanceController {

    private final CreditPlanService creditPlanService;
    private final PaymentService paymentService;
    private final PurchaseService purchaseService;
    private final CurrentUserService currentUserService;

    public StoreFinanceController(
            CreditPlanService creditPlanService,
            PaymentService paymentService,
            PurchaseService purchaseService,
            CurrentUserService currentUserService
    ){
        this.creditPlanService = creditPlanService;
        this.paymentService = paymentService;
        this.purchaseService = purchaseService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/clients/{clientId}/credit-plans")
    public List<CreditPlanResponse> getCreditPlans(
            @PathVariable Long storeId,
            @PathVariable Long clientId
    ){
        currentUserService.validateStoreAdmin(storeId);

        return creditPlanService.getPlansByClient(clientId)
                .stream()
                .filter(plan -> plan.getPurchase().getClientAccount().getStore().getId().equals(storeId))
                .map(creditPlanService::toCreditPlanResponse)
                .toList();
    }

    @GetMapping("/credit-plans/{planId}")
    public CreditPlanResponse getCreditPlan(
            @PathVariable Long storeId,
            @PathVariable Long planId
    ){
        return purchaseService.getCredit(storeId, planId);
    }

    @PostMapping("/credit-plans/{planId}/simulate")
    public CreditPlanResponse simulateCreditPlanUpdate(
            @PathVariable Long storeId,
            @PathVariable Long planId,
            @Valid @RequestBody UpdateCreditRequest request
    ){
        return purchaseService.simulateCreditUpdate(storeId, planId, request);
    }

    @PutMapping("/credit-plans/{planId}")
    public UpdateCreditPlanResponse updateCreditPlan(
            @PathVariable Long storeId,
            @PathVariable Long planId,
            @Valid @RequestBody UpdateCreditRequest request
    ){
        return purchaseService.updateCredit(storeId, planId, request);
    }

    @DeleteMapping("/credit-plans/{planId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCreditPlan(
            @PathVariable Long storeId,
            @PathVariable Long planId
    ){
        purchaseService.deleteCredit(storeId, planId);
    }

    @PostMapping("/installments/{installmentId}/pay")
    public PaymentResponse payInstallment(
            @PathVariable Long storeId,
            @PathVariable Long installmentId,
            @Valid @RequestBody RegisterPaymentRequest request
    ){
        return CreditPlanService.toPaymentResponse(
                paymentService.payInstallment(storeId, installmentId, request.paymentDate())
        );
    }
}
