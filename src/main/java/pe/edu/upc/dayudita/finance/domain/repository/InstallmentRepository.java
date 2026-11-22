package pe.edu.upc.dayudita.finance.domain.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.upc.dayudita.finance.domain.model.Installment;
import pe.edu.upc.dayudita.finance.domain.model.InstallmentStatus;

import java.util.List;
import java.util.Optional;

public interface InstallmentRepository extends JpaRepository<Installment, Long> {

    @Query("""
            select i from Installment i
            where i.creditPlan.id = :creditPlanId and coalesce(i.archived, false) = false
            order by i.installmentNumber asc
            """)
    List<Installment> findCurrentByCreditPlanId(@Param("creditPlanId") Long creditPlanId);

    @Query("""
            select i from Installment i
            where i.creditPlan.id = :creditPlanId and coalesce(i.archived, false) = false
              and i.installmentNumber = :installmentNumber
            """)
    Optional<Installment> findCurrentByCreditPlanIdAndInstallmentNumber(
            @Param("creditPlanId") Long creditPlanId,
            @Param("installmentNumber") Integer installmentNumber
    );

    @Query("""
            select count(i) > 0 from Installment i
            where i.creditPlan.id = :creditPlanId and coalesce(i.archived, false) = false
              and i.status = :status
            """)
    boolean existsCurrentByCreditPlanIdAndStatus(
            @Param("creditPlanId") Long creditPlanId,
            @Param("status") InstallmentStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Installment i where i.id = :id")
    Optional<Installment> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("delete from Installment i where i.creditPlan.id = :creditPlanId")
    int deleteAllByCreditPlanId(@Param("creditPlanId") Long creditPlanId);
}
