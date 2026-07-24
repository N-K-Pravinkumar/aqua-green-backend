package com.aquagreen.repository;
import com.aquagreen.model.ServiceRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
@Repository
public interface ServiceRequestRepository extends JpaRepository<ServiceRequest,Long> {
    // Paginated — use these in list endpoints
    Page<ServiceRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<ServiceRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    Page<ServiceRequest> findByAssignedTechnicianOrderByCreatedAtDesc(String technician, Pageable pageable);
    Page<ServiceRequest> findByStatusAndAssignedTechnicianOrderByCreatedAtDesc(String status, String technician, Pageable pageable);
    // Unpaginated — kept for billing/reports
    List<ServiceRequest> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);

    // Alert queries — find service requests with upcoming filter/service due dates
    @org.springframework.data.jpa.repository.Query(
        "SELECT s FROM ServiceRequest s WHERE s.status='COMPLETED' AND s.nextFilterDueDate IS NOT NULL AND s.nextFilterDueDate BETWEEN :from AND :to ORDER BY s.nextFilterDueDate ASC")
    List<ServiceRequest> findFiltersDueBetween(
        @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
        @org.springframework.data.repository.query.Param("to")   java.time.LocalDateTime to);

    @org.springframework.data.jpa.repository.Query(
        "SELECT s FROM ServiceRequest s WHERE s.status='COMPLETED' AND s.nextServiceDueDate IS NOT NULL AND s.nextServiceDueDate BETWEEN :from AND :to ORDER BY s.nextServiceDueDate ASC")
    List<ServiceRequest> findServicesDueBetween(
        @org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from,
        @org.springframework.data.repository.query.Param("to")   java.time.LocalDateTime to);

    // Customer 360 history — match by mobile since not every ServiceRequest is linked via customer_id
    List<ServiceRequest> findByCustomerMobileOrderByCreatedAtDesc(String customerMobile);

    // Part-replacement / maintenance-reminder feature — scans completed services
    // with logged spare parts to work out per-customer "last replaced" dates.
    List<ServiceRequest> findByStatusAndSparePartsJsonIsNotNullOrderByCompletedAtDesc(String status);

    @org.springframework.data.jpa.repository.Query("SELECT s.serviceCode FROM ServiceRequest s WHERE s.serviceCode IS NOT NULL")
    List<String> findAllServiceCodes();
    List<ServiceRequest> findByServiceCodeIsNullOrderByIdAsc();
}
