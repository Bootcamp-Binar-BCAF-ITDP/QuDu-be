package com.delvin.loan.common;

import java.util.List;
import java.util.stream.Stream;

public class DocumentType {

    private DocumentType() {};

    public static final String KTP = "KTP";
    public static final String KK = "KK";
    public static final String SELFIE = "SELFIE";
    public static final String SLIP_GAJI = "SLIP_GAJI";
    public static final String NPWP = "NPWP";

    public static final String BANK_ACCOUNT = "BANK_ACCOUNT";

    /**
     * Identity papers: they describe the person, so they are uploaded once and
     * reused by every submission.
     */
    public static final List<String> PROFILE_TYPES = List.of(KTP, KK, SELFIE);

    /**
     * Financial evidence. Uploaded afresh for each submission - a payslip six
     * months old says nothing useful about today's ability to repay - but stored
     * against the customer like the rest, so a submission can be refused before
     * it exists rather than patched up afterwards.
     */
    public static final List<String> SUBMISSION_TYPES = List.of(SLIP_GAJI, BANK_ACCOUNT);

    /**
     * How long financial evidence stays acceptable.
     *
     * Applies to {@link #SUBMISSION_TYPES} only: a payslip from six months ago
     * says nothing about today's ability to repay, while a KTP does not go stale
     * in a month. Past this age the document is treated as absent and has to be
     * re-uploaded before the next submission.
     */
    public static final int SUBMISSION_FRESHNESS_DAYS = 30;

    public static boolean expires(String documentType) {
        return SUBMISSION_TYPES.contains(normalize(documentType));
    }

    /**
     * What a branch manager must have in front of them, for a loan application
     * and a limit increase alike. Nothing gets created without all of these.
     */
    public static final List<String> REQUIRED_FOR_SUBMISSION =
            Stream.concat(PROFILE_TYPES.stream(), SUBMISSION_TYPES.stream()).toList();

    public static final List<String> OPTIONAL_PROFILE_TYPES = List.of(NPWP);

    public static final List<String> APPLICATION_TYPES = List.of(SLIP_GAJI, BANK_ACCOUNT);

    public static String normalize(String documentType) {
        return documentType == null ? null : documentType.trim().toUpperCase();
    }

    /**
     * Whether a type may be uploaded to the customer document store.
     *
     * All five required types live there, not just the identity ones: that is
     * what lets a submission be refused for missing paperwork *before* anything
     * is created, instead of leaving an empty application behind for someone to
     * chase. The submission then snapshots them.
     */
    public static boolean isProfileType(String documentType) {
        String normalized = normalize(documentType);
        return REQUIRED_FOR_SUBMISSION.contains(normalized)
                || OPTIONAL_PROFILE_TYPES.contains(normalized);
    }

    public static boolean isRequiredProfileType(String documentType) {
        return PROFILE_TYPES.contains(normalize(documentType));
    }

    public static boolean isApplicationType(String documentType) {
        return APPLICATION_TYPES.contains(normalize(documentType));
    }

    public static String label(String documentType) {
        return switch (normalize(documentType) == null ? "" : normalize(documentType)) {
            case KTP -> "ID card (KTP)";
            case KK -> "Family card (KK)";
            case SELFIE -> "Selfie photo";
            case SLIP_GAJI -> "Payslip";
            case BANK_ACCOUNT -> "Bank passbook";
            case NPWP -> "NPWP";
            default -> documentType;
        };
    }
}
