package com.aquagreen.model;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import java.time.LocalDateTime;

@JsonIgnoreProperties({"hibernateLazyInitializer","handler"})
@Entity @Table(name="automation_rules") @Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AutomationRule {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private String name;
    private String description;
    private String triggerType;
    @Builder.Default private Integer dayOffset = 0;
    private String scheduleType;
    private String scheduleTime;
    private String scheduleDayOfWeek;
    private Integer scheduleDayOfMonth;
    private String actionChannel;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="sms_template_id") private DocumentTemplate smsTemplate;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="whatsapp_template_id") private DocumentTemplate whatsappTemplate;
    @ManyToOne(fetch=FetchType.EAGER) @JoinColumn(name="email_template_id") private DocumentTemplate emailTemplate;
    @Column(columnDefinition="TEXT") private String filterConfig;
    @Builder.Default private Boolean active = true;
    @Builder.Default private Integer totalSent = 0;
    private LocalDateTime lastRunAt;
    private LocalDateTime nextRunAt;
    private String createdBy;
    @Column(updatable=false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @PrePersist protected void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=LocalDateTime.now(); }
    @PreUpdate protected void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
