package com.aquagreen.config;

import com.aquagreen.model.*;
import com.aquagreen.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class MasterSeedConfig implements CommandLineRunner {

    private final AppUserRepository userRepo;
    private final CustomerRepository customerRepo;
    private final LeadRepository leadRepo;
    private final SaleRepository saleRepo;
    private final ServiceRequestRepository serviceRequestRepo;
    private final PaymentRepository paymentRepo;
    private final OperationHistoryRepository historyRepo;
    private final BrandPartnerRepository brandRepo;
    private final PasswordEncoder passwordEncoder;

    // Tamil names for realistic Indian data
    static final String[] FIRST = {"Rajesh","Priya","Murugan","Lakshmi","Dinesh","Kavitha","Suresh",
        "Gomathi","Arun","Meena","Bala","Saranya","Vijay","Deepa","Karthik","Anitha","Senthil",
        "Revathi","Manoj","Pooja","Ramesh","Sunita","Ashok","Nirmala","Prabhu","Vasantha","Kumar",
        "Jayalakshmi","Ganesh","Padma","Selva","Brindha","Ravi","Usha","Mohan","Savitha","Sathish",
        "Kiruthika","Mani","Hemalatha","Shankar","Thenmozhi","Babu","Suganya","Chandran","Radha",
        "Venkat","Malathi","Arjun","Nandhini","Prakash","Sowmya","Siva","Vimala","Harish","Chithra",
        "Saravanan","Amudha","Periyasamy","Geetha","Durai","Mythili","Selvam","Rani","Boopathy"};
    static final String[] LAST = {"Kumar","Raj","Devi","Selvam","Murugan","Krishnan","Rajan","Pillai",
        "Nair","Reddy","Sharma","Pandian","Arumugam","Subramanian","Palaniappan","Ramasamy",
        "Venkatesan","Sundaram","Natarajan","Govindasamy","Annamalai","Subramaniam","Mariappan",
        "Palanisamy","Velayutham","Arunachalam","Thirugnanam","Duraisamy","Ganesan","Karuppusamy"};
    static final String[] CITIES = {"Coimbatore","Saravanampatti","Ganapathy","Peelamedu","RS Puram",
        "Neelikonampalayam","Saibaba Colony","Gandhipuram","Singanallur","Vadavalli","Kuniyamuthur",
        "Podanur","Ukkadam","Ramanathapuram","Vilankurichi","Ondipudur","Selvapuram","Thudiyalur",
        "Kalapatti","Saravanampatty","Kovilpalayam","Vellalore","Kurumbapalayam","Kinathukadavu"};
    static final String[] AREAS = {"Gandhi Nagar","Anna Nagar","Nehru Street","Kamarajar Road",
        "Avinashi Road","Trichy Road","Race Course","Mettupalayam Road","DB Road","Brookefields",
        "Sowripalayam","Tatabad","Ramnagar","Sungam","Lakshmi Mills","Hope College","Stadium",
        "Civil Aerodrome","Town Hall","Maruthamalai Road","Sathy Road","Pappanaickenpalayam"};
    static final String[] PRODUCTS_LIST = {"AGA Classic RO 7L","Aqua Green RO 12L","AGA Pro RO+UV 15L",
        "Commercial RO 25 LPH","Commercial RO 50 LPH","AGA Budget RO 5L","AGA Premium RO 10L",
        "UV Purifier 8L","AGA Grand RO 20L","Commercial RO 100 LPH"};
    static final String[] ISSUES = {"Filter change needed","Water flow reduced","TDS reading high",
        "Leakage from tank","Booster pump noise","Low water pressure","UV lamp not working",
        "Motor failure","Membrane blocked","Annual Annual Service service","No water output","Bad taste",
        "Tank cleaning required","Solenoid valve fault","SMPS not working"};
    static final String[] TECHS = {"Murugan K","Senthil K","Karthik R","Ravi T","Bala S"};
    static final String[] SALESMEN = {"Senthil K","Murugan K","Arun Kumar","Karthik R"};
    static final String[] SOURCES = {"WEBSITE","WHATSAPP","GOOGLE_ADS","FACEBOOK","REFERRAL","INSTAGRAM","WALKIIN"};
    static final String[] LEAD_STATUS = {"NEW","CONTACTED","FOLLOW_UP","QUOTATION_SENT","CONVERTED","LOST"};

    private final Random rand = new Random(42);

    @Override
    public void run(String... args) {
        seedUsers();
        seedBrands();
        seedLargeCustomers();
        seedLargeLeads();
        seedLargeSales();
        seedLargeServiceRequests();
        seedLargePayments();
        seedLargeHistory();
        log.info("=== Master Sample Data Loaded ===");
    }

    void seedUsers() {
        if (userRepo.count() > 0) return;
        String allPerms = "VIEW_LEADS,EDIT_LEADS,DELETE_LEADS,VIEW_CUSTOMERS,EDIT_CUSTOMERS,DELETE_CUSTOMERS,VIEW_PRODUCTS,EDIT_PRODUCTS,VIEW_SERVICES,EDIT_SERVICES,VIEW_SALES,EDIT_SALES,VIEW_SERVICE_REQUESTS,EDIT_SERVICE_REQUESTS,VIEW_QUOTATIONS,EDIT_QUOTATIONS,VIEW_STOCK,EDIT_STOCK,VIEW_EMPLOYEES,EDIT_EMPLOYEES,VIEW_REPORTS,EXPORT_REPORTS,VIEW_COMMUNICATION,SEND_COMMUNICATION,VIEW_GALLERY,EDIT_GALLERY,VIEW_ENQUIRIES,EDIT_ENQUIRIES,MANAGE_USERS";
        String empPerms = "VIEW_LEADS,VIEW_CUSTOMERS,VIEW_SERVICE_REQUESTS,EDIT_SERVICE_REQUESTS,VIEW_ENQUIRIES";
        String mgrPerms = "VIEW_LEADS,EDIT_LEADS,VIEW_CUSTOMERS,EDIT_CUSTOMERS,VIEW_SERVICE_REQUESTS,EDIT_SERVICE_REQUESTS,VIEW_REPORTS,VIEW_ENQUIRIES,EDIT_ENQUIRIES";
        userRepo.saveAll(List.of(
            AppUser.builder().username("mohanbabu").email("mohanbabu@aquagreen.com").password(passwordEncoder.encode(System.getenv().getOrDefault("ADMIN_PASSWORD", "AGA@Admin2026!"))).fullName("Mohan Babu").mobile("9054617008").role("SUPER_ADMIN").permissions("ALL").active(true).build(),
            AppUser.builder().username("admin").email("admin@aquagreen.com").password(passwordEncoder.encode(System.getenv().getOrDefault("STAFF_PASSWORD", "AGA@Staff2026!"))).fullName("Arun Kumar").mobile("9800011111").role("ADMIN").permissions(allPerms).active(true).build(),
            AppUser.builder().username("senthil").email("senthil@aquagreen.com").password(passwordEncoder.encode(System.getenv().getOrDefault("STAFF_PASSWORD", "AGA@Staff2026!"))).fullName("Senthil K").mobile("9800022222").role("MANAGER").permissions(mgrPerms).active(true).build(),
            AppUser.builder().username("murugan").email("murugan@aquagreen.com").password(passwordEncoder.encode(System.getenv().getOrDefault("STAFF_PASSWORD", "AGA@Staff2026!"))).fullName("Murugan K").mobile("9800033333").role("EMPLOYEE").permissions(empPerms).active(true).build(),
            AppUser.builder().username("karthik").email("karthik@aquagreen.com").password(passwordEncoder.encode(System.getenv().getOrDefault("STAFF_PASSWORD", "AGA@Staff2026!"))).fullName("Karthik R").mobile("9800044444").role("EMPLOYEE").permissions(empPerms).active(true).build()
        ));
        log.info("Seeded admin users. Login: mohanbabu@aquagreen.com (check .env for password)");
    }

    void seedBrands() {
        if (brandRepo.count() > 0) return;
        String[][] brandData = {
            {"Kent",          "https://upload.wikimedia.org/wikipedia/commons/thumb/8/8e/Kent_RO_logo.svg/200px-Kent_RO_logo.svg.png"},
            {"Aquaguard",     "https://www.aquaguard.com/favicon.ico"},
            {"Livpure",       "https://www.livpure.in/images/logo.png"},
            {"Pureit",        "https://www.pureitwater.com/IN/sites/default/files/favicon.ico"},
            {"AO Smith",      "https://www.aosmith.com/favicon.ico"},
            {"Blue Star",     "https://www.bluestarindia.com/favicon.ico"},
            {"Havells",       "https://www.havells.com/favicon.ico"},
            {"LG",            "https://www.lg.com/etc/designs/lg-erl-common/clientlibs/images/favicon.ico"},
            {"V-Guard",       "https://www.vguard.in/favicon.ico"},
            {"Eureka Forbes", "https://www.eurekaforbes.com/favicon.ico"},
            {"Aqua Fresh",    ""},
            {"Aqua Grand",    ""},
            {"Aqua Pearl",    ""},
            {"Aqua Crystal",  ""},
            {"Aqua Supreme",  ""},
        };
        List<BrandPartner> list = new ArrayList<>();
        for (int i = 0; i < brandData.length; i++) {
            list.add(BrandPartner.builder().name(brandData[i][0]).logoUrl(brandData[i][1]).displayOrder(i+1).active(true).build());
        }
        brandRepo.saveAll(list);
        log.info("Seeded {} brands", brandData.length);
    }

    void seedLargeCustomers() {
        long existing = customerRepo.count();
        if (existing >= 100) return;
        int toAdd = (int)(500 - existing);
        List<Customer> batch = new ArrayList<>();
        for (int i = 0; i < toAdd; i++) {
            String name = pick(FIRST) + " " + pick(LAST);
            String city = pick(CITIES);
            String area = pick(AREAS);
            String mobile = "9" + (700000000 + rand.nextInt(200000000));
            batch.add(Customer.builder()
                .name(name).mobile(mobile)
                .email(name.toLowerCase().replace(" ",".")+i+"@gmail.com")
                .address(String.format("%d, %s, %s", 1+rand.nextInt(200), area, city))
                .city(city)
                .customerType(rand.nextInt(5) == 0 ? "COMMERCIAL" : "RESIDENTIAL")
                .active(true).build());
        }
        customerRepo.saveAll(batch);
        log.info("Seeded {} customers (total ~500)", toAdd);
    }

    void seedLargeLeads() {
        if (leadRepo.count() >= 100) return;
        List<Customer> customers = customerRepo.findAll();
        List<Lead> batch = new ArrayList<>();
        String[] emps = {"Senthil K","Murugan K","Karthik R","Arun Kumar"};
        for (int i = 0; i < 200; i++) {
            String name = pick(FIRST) + " " + pick(LAST);
            String status = LEAD_STATUS[rand.nextInt(LEAD_STATUS.length)];
            batch.add(Lead.builder()
                .name(name)
                .mobile("8" + (700000000 + rand.nextInt(200000000)))
                .email(name.toLowerCase().replace(" ",".") + i + "@gmail.com")
                .city(pick(CITIES))
                .requirement(pick(PRODUCTS_LIST) + " — " + (rand.nextInt(2)==0 ? "home use" : "office use"))
                .source(pick(SOURCES))
                .assignedEmployee(emps[rand.nextInt(emps.length)])
                .status(status)
                .notes(status.equals("CONVERTED") ? "Customer purchased "+pick(PRODUCTS_LIST) : null)
                .createdAt(LocalDateTime.now().minusDays(rand.nextInt(180)))
                .updatedAt(LocalDateTime.now().minusDays(rand.nextInt(30))).build());
        }
        leadRepo.saveAll(batch);
        log.info("Seeded 200 leads");
    }

    void seedLargeSales() {
        if (saleRepo.count() >= 50) return;
        List<Customer> customers = customerRepo.findAll();
        if (customers.isEmpty()) return;
        List<Sale> batch = new ArrayList<>();
        BigDecimal[] prices = {new BigDecimal("4999"),new BigDecimal("7499"),new BigDecimal("10999"),
            new BigDecimal("14999"),new BigDecimal("18999"),new BigDecimal("3999"),new BigDecimal("6499"),
            new BigDecimal("8999"),new BigDecimal("12999"),new BigDecimal("35000")};
        for (int i = 0; i < 500; i++) {
            Customer c = customers.get(rand.nextInt(customers.size()));
            String product = PRODUCTS_LIST[rand.nextInt(PRODUCTS_LIST.length)];
            BigDecimal price = prices[rand.nextInt(prices.length)];
            int qty = 1 + rand.nextInt(2);
            batch.add(Sale.builder()
                .customer(c).customerName(c.getName())
                .productName(product).quantity(qty)
                .unitPrice(price).totalAmount(price.multiply(BigDecimal.valueOf(qty)))
                .salesPerson(SALESMEN[rand.nextInt(SALESMEN.length)])
                .invoiceNumber("AQG-INV-" + String.format("%04d", i+1))
                .paymentStatus(rand.nextInt(8)==0 ? "PENDING" : "PAID")
                .paymentMethod(new String[]{"CASH","UPI","BANK_TRANSFER","CARD"}[rand.nextInt(4)])
                .createdAt(LocalDateTime.now().minusDays(rand.nextInt(365)))
                .updatedAt(LocalDateTime.now().minusDays(rand.nextInt(30))).build());
        }
        saleRepo.saveAll(batch);
        log.info("Seeded 500 sales");
    }

    void seedLargeServiceRequests() {
        if (serviceRequestRepo.count() >= 50) return;
        List<Customer> customers = customerRepo.findAll();
        if (customers.isEmpty()) return;
        String[] statuses = {"PENDING","ASSIGNED","IN_PROGRESS","COMPLETED","CANCELLED"};
        String[] priorities = {"LOW","MEDIUM","HIGH","URGENT"};
        List<ServiceRequest> batch = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            Customer c = customers.get(rand.nextInt(customers.size()));
            String status = statuses[rand.nextInt(statuses.length)];
            String tech = TECHS[rand.nextInt(TECHS.length)];
            batch.add(ServiceRequest.builder()
                .ticketNumber("SRV-" + String.format("%05d", i+1))
                .customer(c).customerName(c.getName()).customerMobile(c.getMobile())
                .productName(PRODUCTS_LIST[rand.nextInt(PRODUCTS_LIST.length)])
                .issueDescription(ISSUES[rand.nextInt(ISSUES.length)])
                .assignedTechnician(status.equals("PENDING") ? "" : tech)
                .serviceCharge(new BigDecimal(99 + rand.nextInt(900)))
                .status(status).priority(priorities[rand.nextInt(priorities.length)])
                .technicianNotes(status.equals("COMPLETED") ? "Service completed successfully. System working fine." : null)
                .completedAt(status.equals("COMPLETED") ? LocalDateTime.now().minusDays(rand.nextInt(30)) : null)
                .createdAt(LocalDateTime.now().minusDays(rand.nextInt(365)))
                .updatedAt(LocalDateTime.now().minusDays(rand.nextInt(30))).build());
        }
        serviceRequestRepo.saveAll(batch);
        log.info("Seeded 1000 service requests");
    }

    void seedLargePayments() {
        if (paymentRepo.count() >= 50) return;
        List<Sale> sales = saleRepo.findAllByOrderByCreatedAtDesc();
        if (sales.isEmpty()) return;
        List<Payment> batch = new ArrayList<>();
        int count = 0;
        for (Sale s : sales) {
            if ("PAID".equals(s.getPaymentStatus())) {
                batch.add(Payment.builder()
                    .paymentNumber("AQG-PAY-" + String.format("%04d", ++count))
                    .customer(s.getCustomer()).customerName(s.getCustomerName())
                    .sale(s).invoiceNumber(s.getInvoiceNumber())
                    .amount(s.getTotalAmount()).paymentMethod(s.getPaymentMethod())
                    .paymentStatus("PAID").receivedBy(SALESMEN[rand.nextInt(SALESMEN.length)])
                    .createdAt(s.getCreatedAt().plusHours(rand.nextInt(48))).build());
            }
            if (count >= 500) break;
        }
        paymentRepo.saveAll(batch);
        log.info("Seeded {} payments", batch.size());
    }

    void seedLargeHistory() {
        if (historyRepo.count() >= 100) return;
        List<Customer> customers = customerRepo.findAll();
        if (customers.isEmpty()) return;
        String[] actions = {"CUSTOMER_CREATED","LEAD_CREATED","PRODUCT_PURCHASED","QUOTATION_GENERATED",
            "INSTALLATION_COMPLETED","SERVICE_COMPLETED","FILTER_REPLACED","PAYMENT_RECEIVED",
            "EMPLOYEE_ASSIGNED","COMMUNICATION_SENT","Annual Service_RENEWED","COMPLAINT_REGISTERED"};
        List<OperationHistory> batch = new ArrayList<>();
        List<Customer> sample = customers.subList(0, Math.min(50, customers.size()));
        for (Customer c : sample) {
            for (int i = 0; i < rand.nextInt(8)+2; i++) {
                String action = actions[rand.nextInt(actions.length)];
                batch.add(OperationHistory.builder()
                    .action(action).entityType("Customer").entityId(c.getId())
                    .entityName(c.getName()).customer(c)
                    .performedBy(SALESMEN[rand.nextInt(SALESMEN.length)])
                    .remarks(action.replace("_"," ") + " — " + c.getName())
                    .createdAt(LocalDateTime.now().minusDays(rand.nextInt(365))).build());
            }
        }
        historyRepo.saveAll(batch);
        log.info("Seeded {} history records", batch.size());
    }

    private String pick(String[] arr) { return arr[rand.nextInt(arr.length)]; }
}
