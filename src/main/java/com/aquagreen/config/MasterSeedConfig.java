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
    private final BrandPartnerRepository brandRepo;
    private final PasswordEncoder passwordEncoder;


    @Override
    public void run(String... args) {
        seedUsers();
        seedBrands();
        // NOTE: seedLargeCustomers/Leads/Sales/ServiceRequests/Payments/History
        // are deliberately NOT called — they generated ~500 fake customers, 200
        // fake leads, 500 fake sales, 1000 fake service tickets and 500 fake
        // payments using a Tamil-name-pool random generator. Real business data
        // now comes from the actual website and admin entry — this app should
        // never auto-generate business records again, even on a fresh database.
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

}
