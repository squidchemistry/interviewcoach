package com.aicoach.user.service;

import com.aicoach.user.dto.CompanyResponse;
import com.aicoach.user.repository.CompanyRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

    private final CompanyRepository companies;

    public CompanyService(CompanyRepository companies) {
        this.companies = companies;
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> list() {
        return companies.findAllByOrderByNameAsc().stream().map(UserMapper::toResponse).toList();
    }
}
