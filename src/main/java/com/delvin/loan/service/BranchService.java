package com.delvin.loan.service;

import com.delvin.loan.common.CacheNames;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.evict.EvictsBranchCaches;
import com.delvin.loan.dto.request.branch.BranchRequest;
import com.delvin.loan.dto.response.branch.BranchResponse;
import com.delvin.loan.model.Branch;
import com.delvin.loan.repository.BranchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BranchService {

    private final BranchRepository branchRepository;

    @Cacheable(cacheNames = CacheNames.BRANCH_PAGE, keyGenerator = "pageKeyGenerator")
    public PageResponse<BranchResponse> getAllBranches(String search, Pageable pageable) {
        Page<Branch> branches = branchRepository.searchActive(toKeyword(search), pageable);

        return PageResponse.of(branches, this::toResponse);
    }

    @Cacheable(cacheNames = CacheNames.BRANCH_OPTIONS)
    public List<BranchResponse> getBranchOptions() {
        return branchRepository.findByIsActiveOrderByBranchNameAsc(true)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Cacheable(cacheNames = CacheNames.BRANCH_BY_ID, key = "#branchCode")
    public BranchResponse getBranchById(String branchCode) {
        Branch branch = branchRepository.findByBranchCodeAndIsActive(branchCode, true)
                .orElseThrow(() -> new RuntimeException("Branch not found"));
        return toResponse(branch);
    }

    @Transactional
    @EvictsBranchCaches
    public BranchResponse createBranch(BranchRequest request) {
        if (branchRepository.findByBranchCodeAndIsActive(request.getBranchCode(), true).isPresent()) {
            throw new RuntimeException("Branch code already exists.");
        }

        Branch branch = new Branch();
        branch.setBranchCode(request.getBranchCode());
        branch.setBranchName(request.getBranchName());
        branch.setLocation(request.getLocation());
        branch.setEmail(request.getEmail());
        branch.setPhoneNumber(request.getPhoneNumber());
        branch.setIsActive(request.getIsActive());

        return toResponse(branchRepository.save(branch));
    }

    @Transactional
    @EvictsBranchCaches
    public BranchResponse updateBranch(Integer id, BranchRequest request) {
        Branch branch = branchRepository.findByBranchIdAndIsActive(id, true)
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        branch.setBranchName(request.getBranchName());
        branch.setLocation(request.getLocation());
        branch.setEmail(request.getEmail());
        branch.setPhoneNumber(request.getPhoneNumber());
        branch.setIsActive(request.getIsActive());

        return toResponse(branchRepository.save(branch));
    }

    @Transactional
    @EvictsBranchCaches
    public BranchResponse deleteBranch(Integer id) {
        Branch branch = branchRepository.findByBranchIdAndIsActive(id, true)
                .orElseThrow(() -> new RuntimeException("Branch not found"));
        branch.setIsActive(false);
        return toResponse(branchRepository.save(branch));
    }

    private String toKeyword(String search) {

        if (search == null || search.isBlank()) {
            return "%%";
        }

        return "%" + search.trim().toLowerCase() + "%";
    }

    private BranchResponse toResponse(Branch branch) {

        BranchResponse response = new BranchResponse();

        response.setBranchId(branch.getBranchId());
        response.setBranchCode(branch.getBranchCode());
        response.setBranchName(branch.getBranchName());
        response.setLocation(branch.getLocation());
        response.setEmail(branch.getEmail());
        response.setPhoneNumber(branch.getPhoneNumber());
        response.setIsActive(branch.getIsActive());

        return response;
    }
}