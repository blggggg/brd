package com.brd;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/companies")
public class Companies {
    private final CompanyMapper companies;

    Companies(CompanyMapper companies) {
        this.companies = companies;
    }

    @GetMapping
    List<Map<String,Object>> list(HttpServletRequest request) {
        AccountGate.account(request);
        return companies.listEnabled();
    }
}
