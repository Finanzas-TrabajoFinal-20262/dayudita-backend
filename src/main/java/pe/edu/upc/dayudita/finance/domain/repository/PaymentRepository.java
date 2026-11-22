package pe.edu.upc.dayudita.finance.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upc.dayudita.finance.domain.model.Payment;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInstallment_CreditPlan_Purchase_ClientAccount_Client_IdOrderByPaymentDateDesc(Long clientId);

    List<Payment> findByInstallment_CreditPlan_IdOrderByPaymentDateAscIdAsc(Long creditPlanId);

    boolean existsByInstallment_Id(Long installmentId);

    @Modifying
    @Query("""
            delete from Payment p
            where p.installment.id in (select i.id from Installment i where i.creditPlan.id = :creditPlanId)
            """)
    int deleteAllByCreditPlanId(@Param("creditPlanId") Long creditPlanId);
}
