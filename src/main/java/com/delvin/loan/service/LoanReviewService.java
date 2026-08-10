package com.delvin.loan.service;

import com.delvin.loan.common.LoanStatus;
import com.delvin.loan.common.RecommendationStatus;
import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.loanreq.LoanReviewRequest;
import com.delvin.loan.dto.response.loanresp.LoanReviewResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.LoanApplication;
import com.delvin.loan.model.LoanReview;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.LoanReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class LoanReviewService {

    private final LoanReviewRepository reviewRepository;
    private final LoanApplicationService applicationService;
    private final LoanMapper mapper;

    public LoanReviewService(LoanReviewRepository reviewRepository,
                              LoanApplicationService applicationService,
                              LoanMapper mapper) {
        this.reviewRepository = reviewRepository;
        this.applicationService = applicationService;
        this.mapper = mapper;
    }

    @Transactional
    public LoanReviewResponse submitReview(LoanReviewRequest request) {
        String recommendation = normalizeRecommendation(request.getRecommendation());

        User marketing = applicationService.getUserWithRole(request.getMarketingUserId(), RoleName.MARKETING);
        LoanApplication application = applicationService.getApplicationOrThrow(request.getApplicationId());

        if (!LoanStatus.CHECKING.equals(application.getStatus())) {
            throw BusinessException.conflict(
                    "Application " + application.getApplicationId() + " is not awaiting marketing review (current status: "
                            + application.getStatus() + ")");
        }
        if (reviewRepository.existsByApplication_ApplicationId(application.getApplicationId())) {
            throw BusinessException.conflict("Application already has a review.");
        }

        LoanReview review = new LoanReview();
        review.setApplication(application);
        review.setMarketing(marketing);
        review.setRecommendation(recommendation);
        review.setReviewNote(request.getReviewNote());
        review.setUploadedAt(LocalDate.now());
        reviewRepository.save(review);

        application.setStatus(RecommendationStatus.ACCEPT.equals(recommendation)
                ? LoanStatus.PENDING_BRANCH_MANAGER
                : LoanStatus.REJECTED_BY_MARKETING);

        return mapper.toReviewResponse(review);
    }

    public LoanReviewResponse getByApplication(String applicationId) {
        LoanReview review = reviewRepository.findByApplication_ApplicationId(applicationId)
                .orElseThrow(() -> BusinessException.notFound("No review found for application " + applicationId));
        return mapper.toReviewResponse(review);
    }

    private String normalizeRecommendation(String recommendation) {
        if (recommendation == null) {
            throw BusinessException.badRequest("recommendation is required");
        }
        if (recommendation.equalsIgnoreCase(RecommendationStatus.ACCEPT)) return RecommendationStatus.ACCEPT;
        if (recommendation.equalsIgnoreCase(RecommendationStatus.REJECT)) return RecommendationStatus.REJECT;
        throw BusinessException.badRequest("recommendation must be ACCEPT or REJECT");
    }
}
