package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.common.ResponseUtil;
import com.delvin.loan.dto.request.customer.CustomerProfileUpdateRequest;
import com.delvin.loan.dto.request.device.DeviceTokenRequest;
import com.delvin.loan.dto.request.loanreq.LoanApplicationCreateRequest;
import com.delvin.loan.dto.request.plafond.PlafondUpgradeRequest;
import com.delvin.loan.dto.response.customer.CustomerDocumentResponse;
import com.delvin.loan.dto.response.customer.CustomerProfileResponse;
import com.delvin.loan.dto.response.loanresp.LoanApplicationResponse;
import com.delvin.loan.dto.response.loanresp.LoanDocumentResponse;
import com.delvin.loan.dto.response.notification.NotificationResponse;
import com.delvin.loan.dto.response.plafond.CustomerPlafondResponse;
import com.delvin.loan.dto.response.plafond.PlafondRequestResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.AppUser;
import com.delvin.loan.service.CustomerDocumentService;
import com.delvin.loan.service.CustomerService;
import com.delvin.loan.service.LoanApplicationService;
import com.delvin.loan.service.LoanDocumentService;
import com.delvin.loan.service.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final LoanApplicationService applicationService;
    private final LoanDocumentService documentService;
    private final CustomerDocumentService customerDocumentService;
    private final NotificationService notificationService;

    @PostMapping
    public ResponseEntity<ApiResponse<LoanApplicationResponse>> create(
            @AuthenticationPrincipal AppUser appUser,
            @Valid @RequestBody LoanApplicationCreateRequest request) {

        requireSelf(appUser, request.getCustomerId());

        return ResponseUtil.created(
                "Loan application submitted",
                customerService.createApplication(request));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<ApiResponse<PageResponse<LoanApplicationResponse>>> listByCustomer(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable String customerId,
            @PageableDefault(size = 10, sort = "submissionDate", direction = Sort.Direction.DESC) Pageable pageable) {

        requireSelf(appUser, customerId);

        return ResponseUtil.success(
                "Loan applications retrieved",
                applicationService.listByCustomer(customerId, pageable));
    }

    @PostMapping(value = "/applications/{applicationId}/documents", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<LoanDocumentResponse>> uploadDocument(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable String applicationId,
            @RequestParam String documentType,
            @RequestParam("file") MultipartFile file) {

        return ResponseUtil.created(
                "Document uploaded",
                documentService.uploadOwnDocument(appUser.getUserId(), applicationId, documentType, file));
    }

    @GetMapping("/applications/{applicationId}/documents")
    public ResponseEntity<ApiResponse<List<LoanDocumentResponse>>> listDocuments(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable String applicationId) {

        return ResponseUtil.success(
                "Documents retrieved",
                documentService.listOwnDocuments(appUser.getUserId(), applicationId));
    }

    // PROFILE
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> getProfile(
            @AuthenticationPrincipal AppUser appUser) {

        return ResponseUtil.success("Profile retrieved",
                customerService.getProfile(appUser.getUserId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<CustomerProfileResponse>> updateProfile(
            @AuthenticationPrincipal AppUser appUser,
            @Valid @RequestBody CustomerProfileUpdateRequest request) {

        return ResponseUtil.success("Profile updated",
                customerService.updateProfile(appUser.getUserId(), request));
    }

    @PostMapping(value = "/documents", consumes = "multipart/form-data")
    public ResponseEntity<ApiResponse<CustomerDocumentResponse>> uploadProfileDocument(
            @AuthenticationPrincipal AppUser appUser,
            @RequestParam String documentType,
            @RequestParam("file") MultipartFile file) {

        return ResponseUtil.created("Profile document saved",
                customerDocumentService.upload(appUser.getUserId(), documentType, file));
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<List<CustomerDocumentResponse>>> listProfileDocuments(
            @AuthenticationPrincipal AppUser appUser) {

        return ResponseUtil.success("Profile documents retrieved",
                customerDocumentService.list(appUser.getUserId()));
    }

    // PLAFOND
    @GetMapping("/plafond")
    public ResponseEntity<ApiResponse<CustomerPlafondResponse>> getMyPlafond(
            @AuthenticationPrincipal AppUser appUser) {

        return ResponseUtil.success("Customer plafond retrieved successfully",
                customerService.getMyPlafond(appUser.getUserId()));
    }

    @PostMapping("/plafond")
    public ResponseEntity<ApiResponse<PlafondRequestResponse>> requestPlafond(
            @AuthenticationPrincipal AppUser appUser,
            @Valid @RequestBody PlafondUpgradeRequest request) {

        return ResponseUtil.created("Plafond request submitted successfully",
                customerService.requestPlafond(appUser.getUserId(), request));
    }

    /** History of the customer's own limit requests. */
    @GetMapping("/plafond/requests")
    public ResponseEntity<ApiResponse<List<PlafondRequestResponse>>> myPlafondRequests(
            @AuthenticationPrincipal AppUser appUser) {

        return ResponseUtil.success("Plafond requests retrieved successfully",
                customerService.getMyPlafondRequests(appUser.getUserId()));
    }

    // NOTIFICATIONS
    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> myNotifications(
            @AuthenticationPrincipal AppUser appUser,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseUtil.success("Notifications retrieved",
                notificationService.list(appUser.getUserId(), pageable));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadNotificationCount(
            @AuthenticationPrincipal AppUser appUser) {

        return ResponseUtil.success("Unread notification count retrieved",
                Map.of("unread", notificationService.unreadCount(appUser.getUserId())));
    }

    @PutMapping("/notifications/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markNotificationRead(
            @AuthenticationPrincipal AppUser appUser,
            @PathVariable Long notificationId) {

        return ResponseUtil.success("Notification marked as read",
                notificationService.markRead(appUser.getUserId(), notificationId));
    }

    @PutMapping("/notifications/read")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllNotificationsRead(
            @AuthenticationPrincipal AppUser appUser) {

        return ResponseUtil.success("Notifications marked as read",
                Map.of("updated", notificationService.markAllRead(appUser.getUserId())));
    }

    // DEVICE TOKENS (step 6 - push notifications)
    @PostMapping("/device-tokens")
    public ResponseEntity<ApiResponse<Void>> registerDeviceToken(
            @AuthenticationPrincipal AppUser appUser,
            @Valid @RequestBody DeviceTokenRequest request) {

        customerService.registerDeviceToken(appUser.getUserId(), request);
        return ResponseUtil.success("Device token registered", null);
    }

    @DeleteMapping("/device-tokens")
    public ResponseEntity<ApiResponse<Void>> removeDeviceToken(
            @AuthenticationPrincipal AppUser appUser,
            @RequestParam String token) {

        customerService.removeDeviceToken(appUser.getUserId(), token);
        return ResponseUtil.success("Device token removed", null);
    }

    private void requireSelf(AppUser appUser, String customerId) {

        if (appUser == null) {
            throw BusinessException.unauthorized("Not authenticated");
        }

        if (customerId != null && !customerId.equals(appUser.getUserId())) {
            throw BusinessException.forbidden("You can only act on your own account");
        }
    }
}
