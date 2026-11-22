package pe.edu.upc.dayudita.finance.interfaces.rest.dto;

import pe.edu.upc.dayudita.finance.domain.model.CreditPlanStatus;
import pe.edu.upc.dayudita.sales.interfaces.rest.dto.PurchaseItemResponse;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreditPlanResponse(
        Long id,
        Long purchaseId,
        Long clientId,
        Long storeId,
        LocalDate purchaseDate,
        BigDecimal ventaPrecio,
        BigDecimal principal,
        BigDecimal teaPactada,
        BigDecimal teq,
        BigDecimal teaMoratoriaPactada,
        Integer paymentPeriodDays,
        Integer numeroMeses,
        Integer installmentCount,
        Integer periodosGracia,
        Integer diasGraciaTotal,
        BigDecimal totalIntereses,
        BigDecimal interesCapitalizado,
        BigDecimal totalAmortizacion,
        BigDecimal totalCuotas,
        BigDecimal totalAPagar,
        BigDecimal totalInteresMoratorio,
        BigDecimal totalAPagarConMora,
        CreditPlanStatus status,
        List<PurchaseItemResponse> items,
        List<InstallmentResponse> installments,
        BigDecimal montoPagadoHistorico,
        List<PaymentResponse> historicalPayments
) {
}
