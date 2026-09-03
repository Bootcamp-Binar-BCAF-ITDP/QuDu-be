package com.delvin.loan.service;

import com.delvin.loan.common.PageResponse;
import com.delvin.loan.dto.request.plafond.PlafondRequest;
import com.delvin.loan.dto.response.plafond.PlafondResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.Plafond;
import com.delvin.loan.repository.PlafondRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PlafondService {

    public static final int DEFAULT_LEVEL = 1;

    private final PlafondRepository plafondRepository;

    // Master data (admin)
    @Transactional(readOnly = true)
    public PageResponse<PlafondResponse> getAll(String search, Pageable pageable) {

        Page<Plafond> plafonds = plafondRepository.search(toKeyword(search), pageable);

//        List<Plafond> plafonds = activeOnly
//                ? plafondRepository.findAllByIsActiveTrueOrderByLevelAsc()
//                : plafondRepository.findAllByOrderByLevelAsc();

        return PageResponse.of(plafonds, this::toResponse);
    }

    @Transactional(readOnly = true)
    public PlafondResponse getById(Integer plafondId) {
        return PlafondResponse.from(findById(plafondId));
    }

    @Transactional(readOnly = true)
    public PlafondResponse getByLevel(Integer level) {
        return PlafondResponse.from(findByLevel(level));
    }

    @Transactional
    public PlafondResponse create(PlafondRequest request) {

        validateRanges(request);

        if (plafondRepository.existsByLevel(request.getLevel())) {
            throw BusinessException.conflict("Plafond level " + request.getLevel() + " already exists");
        }

        validateNoOverlap(request, null);

        Plafond plafond = new Plafond();
        applyRequest(plafond, request);

        return PlafondResponse.from(plafondRepository.save(plafond));
    }

    @Transactional
    public PlafondResponse update(Integer plafondId, PlafondRequest request) {

        validateRanges(request);

        Plafond plafond = findById(plafondId);

        plafondRepository.findByLevel(request.getLevel())
                .filter(existing -> !existing.getPlafondId().equals(plafondId))
                .ifPresent(existing -> {
                    throw BusinessException.conflict("Plafond level " + request.getLevel() + " already exists");
                });

        validateNoOverlap(request, plafondId);

        // Level 1 is what every new customer gets, so it can never be switched off.
        if (Objects.equals(plafond.getLevel(), DEFAULT_LEVEL) && Boolean.FALSE.equals(request.getIsActive())) {
            throw BusinessException.badRequest(
                    "The default plafond (level " + DEFAULT_LEVEL + ") cannot be deactivated");
        }

        applyRequest(plafond, request);

        return PlafondResponse.from(plafondRepository.save(plafond));
    }

    @Transactional
    public void delete(Integer plafondId) {

        Plafond plafond = findById(plafondId);

        if (Objects.equals(plafond.getLevel(), DEFAULT_LEVEL)) {
            throw BusinessException.badRequest(
                    "The default plafond (level " + DEFAULT_LEVEL + ") cannot be deleted");
        }

        plafond.setIsActive(false);
        plafondRepository.save(plafond);
    }

    // Shared with CustomerService / BranchManagerService
    @Transactional(readOnly = true)
    public Plafond getDefaultPlafond() {
        return plafondRepository.findByLevel(DEFAULT_LEVEL)
                .orElseThrow(() -> BusinessException.notFound(
                        "Default plafond (level " + DEFAULT_LEVEL + ") is not configured"));
    }

    public Customer assignDefaultPlafond(Customer customer) {

        if (customer.getPlafond() == null) {

            Plafond defaultPlafond = getDefaultPlafond();

            customer.setPlafond(defaultPlafond);
            customer.setApprovedLimit(defaultPlafond.getMaxAmount());
        }

        return customer;
    }

    @Transactional(readOnly = true)
    public Plafond resolveByAmount(BigDecimal amount) {
        return plafondRepository
                .findFirstByIsActiveTrueAndMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqualOrderByLevelAsc(
                        amount, amount)
                .orElseThrow(() -> BusinessException.badRequest("No plafond level covers the amount " + amount));
    }

    @Transactional(readOnly = true)
    public PlafondResponse simulate(BigDecimal requestedAmount) {
        return PlafondResponse.from(resolveByAmount(requestedAmount));
    }

    // Helpers
    private PlafondResponse toResponse(Plafond plafond) {
        PlafondResponse response = new PlafondResponse();

        response.setPlafondId(plafond.getPlafondId());
        response.setAdminFee(plafond.getAdminFee());
        response.setDescription(plafond.getDescription());
        response.setInterestRate(plafond.getInterestRate());
        response.setLevel(plafond.getLevel());
        response.setMaxAmount(plafond.getMaxAmount());
        response.setMinimumAmount(plafond.getMinimumAmount());
        response.setMaxTenor(plafond.getMaxTenor());
        response.setMinTenor(plafond.getMinTenor());

        return response;
    }

    private String toKeyword(String search) {

        if (search == null || search.isBlank()) {
            return "%%";
        }

        return "%" + search.trim().toLowerCase() + "%";
    }

    private Plafond findById(Integer plafondId) {
        return plafondRepository.findById(plafondId)
                .orElseThrow(() -> BusinessException.notFound("Plafond with id " + plafondId + " not found"));
    }

    private Plafond findByLevel(Integer level) {
        return plafondRepository.findByLevel(level)
                .orElseThrow(() -> BusinessException.notFound("Plafond with level " + level + " not found"));
    }

    private void validateRanges(PlafondRequest request) {

        if (request.getMinimumAmount().compareTo(request.getMaxAmount()) > 0) {
            throw BusinessException.badRequest("minimumAmount cannot be greater than maxAmount");
        }

        if (request.getMinTenor() > request.getMaxTenor()) {
            throw BusinessException.badRequest("minTenor cannot be greater than maxTenor");
        }
    }

    private void validateNoOverlap(PlafondRequest request, Integer excludedPlafondId) {

        plafondRepository
                .findByIsActiveTrueAndMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqual(
                        request.getMaxAmount(), request.getMinimumAmount())
                .stream()
                .filter(existing -> !existing.getPlafondId().equals(excludedPlafondId))
                .findFirst()
                .ifPresent(existing -> {
                    throw BusinessException.conflict(
                            "Amount range overlaps with plafond level " + existing.getLevel()
                                    + " (" + existing.getMinimumAmount() + " - " + existing.getMaxAmount() + ")");
                });
    }

    private void applyRequest(Plafond plafond, PlafondRequest request) {
        plafond.setLevel(request.getLevel());
        plafond.setDescription(request.getDescription());
        plafond.setMinimumAmount(request.getMinimumAmount());
        plafond.setMaxAmount(request.getMaxAmount());
        plafond.setMinTenor(request.getMinTenor());
        plafond.setMaxTenor(request.getMaxTenor());
        plafond.setInterestRate(request.getInterestRate());
        plafond.setAdminFee(request.getAdminFee());
        plafond.setIsActive(request.getIsActive() != null ? request.getIsActive() : Boolean.TRUE);
    }
}