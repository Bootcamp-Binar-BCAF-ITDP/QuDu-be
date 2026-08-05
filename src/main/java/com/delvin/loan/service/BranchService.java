package com.delvin.loan.service;

import com.delvin.loan.model.Branch;
import com.delvin.loan.repository.BranchRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BranchService {

    private final BranchRepository branchRepository;

    public BranchService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    public List<Branch> getAllBranches() {
        return branchRepository.findAll();
    }

    public Optional<Branch> getBranchById(String id) {
        return branchRepository.findById(id);
    }

    public Branch createBranch(Branch branch) {
        return branchRepository.save(branch);
    }

    public Branch updateBranch(String id, Branch updatedBranch) {

        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        branch.setBranchName(updatedBranch.getBranchName());
        branch.setLocation(updatedBranch.getLocation());
        branch.setEmail(updatedBranch.getEmail());
        branch.setPhoneNumber(updatedBranch.getPhoneNumber());
        branch.setIsActive(updatedBranch.getIsActive());

        return branchRepository.save(branch);
    }

    public void deleteBranch(String id) {

        Branch branch = branchRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Branch not found"));

        branchRepository.delete(branch);
    }
}

