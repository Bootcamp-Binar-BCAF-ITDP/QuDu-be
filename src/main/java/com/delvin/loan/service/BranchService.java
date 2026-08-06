package com.delvin.loan.service;

import com.delvin.loan.dto.request.branch.BranchRequest;
import com.delvin.loan.dto.response.branch.BranchResponse;
import com.delvin.loan.model.Branch;
import com.delvin.loan.repository.BranchRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BranchService {

    private final BranchRepository branchRepository;

    public BranchService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    public List<BranchResponse> getAllBranches() {

        return branchRepository.findByIsActiveOrderByBranchIdAsc(true)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public BranchResponse getBranchById(String branchCode) {
        Branch branch = branchRepository.findByBranchCodeAndIsActive(branchCode, true)
                .orElseThrow(() -> new RuntimeException("Branch not found"));
        return toResponse(branch);
    }

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

    public BranchResponse deleteBranch(Integer id) {
        Branch branch = branchRepository.findByBranchIdAndIsActive(id, true)
                .orElseThrow(() -> new RuntimeException("Branch not found"));
        branch.setIsActive(false);
        return toResponse(branchRepository.save(branch));
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