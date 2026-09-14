package com.delvin.loan.service;

import com.delvin.loan.dto.request.plafond.PlafondRequest;
import com.delvin.loan.dto.response.plafond.PlafondResponse;
import com.delvin.loan.exception.BusinessException;
import com.delvin.loan.model.Customer;
import com.delvin.loan.model.Plafond;
import com.delvin.loan.repository.PlafondRepository;
import com.delvin.loan.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlafondServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 10);

    @Mock
    private PlafondRepository plafondRepository;

    @InjectMocks
    private PlafondService service;

    private PlafondRequest request;

    @BeforeEach
    void setUp() {
        request = new PlafondRequest();
        request.setLevel(3);
        request.setDescription("Gold");
        request.setMinimumAmount(BigDecimal.valueOf(20_000_000));
        request.setMaxAmount(BigDecimal.valueOf(50_000_000));
        request.setMinTenor(6);
        request.setMaxTenor(24);
        request.setInterestRate(BigDecimal.valueOf(0.1));
        request.setAdminFee(BigDecimal.valueOf(250_000));
        request.setIsActive(true);
    }

    private void stubNoOverlap() {
        when(plafondRepository
                .findByIsActiveTrueAndMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqual(any(), any()))
                .thenReturn(List.of());
    }

    @Test
    @DisplayName("a blank search becomes a match-everything keyword")
    void blankSearchMatchesEverything() {
        when(plafondRepository.search(any(), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(TestFixtures.plafond(1, 1_000_000L, 10_000_000L)), PAGE, 1));

        service.getAll("   ", PAGE);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(plafondRepository).search(keyword.capture(), eq(PAGE));
        assertThat(keyword.getValue()).isEqualTo("%%");
    }

    @Test
    @DisplayName("the catalog lists only active tiers, ordered by level")
    void catalogListsOnlyActiveTiers() {
        when(plafondRepository.findAllByIsActiveTrueOrderByLevelAsc())
                .thenReturn(List.of(
                        TestFixtures.plafond(1, 1_000_000L, 10_000_000L),
                        TestFixtures.plafond(2, 10_000_001L, 25_000_000L)));

        assertThat(service.catalog())
                .extracting(PlafondResponse::getLevel)
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("an inactive tier is absent from the catalog even though it still exists")
    void inactiveTierIsHiddenFromCatalog() {
        when(plafondRepository.findAllByIsActiveTrueOrderByLevelAsc()).thenReturn(List.of());

        assertThat(service.catalog()).isEmpty();
    }

    @Test
    @DisplayName("an unknown plafond id is a 404")
    void unknownIdIsNotFound() {
        when(plafondRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(404))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Plafond with id 404 not found")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("an unknown plafond level is a 404")
    void unknownLevelIsNotFound() {
        when(plafondRepository.findByLevel(9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByLevel(9))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Plafond with level 9 not found");
    }

    @Test
    @DisplayName("a minimum above the maximum is refused")
    void refusesInvertedAmountRange() {
        request.setMinimumAmount(BigDecimal.valueOf(60_000_000));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("minimumAmount cannot be greater than maxAmount");

        verify(plafondRepository, never()).save(any());
    }

    @Test
    @DisplayName("a minimum tenor above the maximum is refused")
    void refusesInvertedTenorRange() {
        request.setMinTenor(36);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("minTenor cannot be greater than maxTenor");
    }

    @Test
    @DisplayName("a duplicate level is refused")
    void refusesDuplicateLevel() {
        when(plafondRepository.existsByLevel(3)).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Plafond level 3 already exists")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("an amount range that overlaps an existing tier is refused, and names it")
    void refusesOverlappingRange() {
        when(plafondRepository.existsByLevel(3)).thenReturn(false);
        when(plafondRepository
                .findByIsActiveTrueAndMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqual(any(), any()))
                .thenReturn(List.of(TestFixtures.plafond(2, 10_000_000L, 30_000_000L)));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Amount range overlaps with plafond level 2");

        verify(plafondRepository, never()).save(any());
    }

    @Test
    @DisplayName("a valid tier is saved with every field from the request")
    void createsTier() {
        when(plafondRepository.existsByLevel(3)).thenReturn(false);
        stubNoOverlap();
        when(plafondRepository.save(any(Plafond.class))).thenAnswer(i -> i.getArgument(0));

        service.create(request);

        ArgumentCaptor<Plafond> captor = ArgumentCaptor.forClass(Plafond.class);
        verify(plafondRepository).save(captor.capture());

        Plafond saved = captor.getValue();
        assertThat(saved.getLevel()).isEqualTo(3);
        assertThat(saved.getDescription()).isEqualTo("Gold");
        assertThat(saved.getMinimumAmount()).isEqualByComparingTo(BigDecimal.valueOf(20_000_000));
        assertThat(saved.getMaxAmount()).isEqualByComparingTo(BigDecimal.valueOf(50_000_000));
        assertThat(saved.getMinTenor()).isEqualTo(6);
        assertThat(saved.getMaxTenor()).isEqualTo(24);
        assertThat(saved.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("a tier with no active flag defaults to active")
    void missingActiveFlagDefaultsToActive() {
        request.setIsActive(null);
        when(plafondRepository.existsByLevel(3)).thenReturn(false);
        stubNoOverlap();
        when(plafondRepository.save(any(Plafond.class))).thenAnswer(i -> i.getArgument(0));

        service.create(request);

        ArgumentCaptor<Plafond> captor = ArgumentCaptor.forClass(Plafond.class);
        verify(plafondRepository).save(captor.capture());
        assertThat(captor.getValue().getIsActive()).isTrue();
    }

    @Test
    @DisplayName("a tier may keep its own level on update without tripping the duplicate check")
    void updateAllowsKeepingItsOwnLevel() {
        Plafond existing = TestFixtures.plafond(3, 20_000_000L, 50_000_000L);
        when(plafondRepository.findById(3)).thenReturn(Optional.of(existing));
        when(plafondRepository.findByLevel(3)).thenReturn(Optional.of(existing));
        when(plafondRepository
                .findByIsActiveTrueAndMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqual(any(), any()))
                .thenReturn(List.of(existing));
        when(plafondRepository.save(any(Plafond.class))).thenAnswer(i -> i.getArgument(0));

        service.update(3, request);

        verify(plafondRepository).save(existing);
    }

    @Test
    @DisplayName("taking a level that another tier already holds is refused")
    void updateRefusesLevelHeldByAnother() {
        Plafond target = TestFixtures.plafond(5, 60_000_000L, 90_000_000L);
        Plafond other = TestFixtures.plafond(3, 20_000_000L, 50_000_000L);

        when(plafondRepository.findById(5)).thenReturn(Optional.of(target));
        when(plafondRepository.findByLevel(3)).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(5, request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Plafond level 3 already exists");
    }

    @Test
    @DisplayName("the default tier cannot be deactivated, because every new customer lands on it")
    void defaultTierCannotBeDeactivated() {
        request.setLevel(PlafondService.DEFAULT_LEVEL);
        request.setIsActive(false);

        Plafond defaultTier = TestFixtures.plafond(PlafondService.DEFAULT_LEVEL, 1_000_000L, 10_000_000L);
        when(plafondRepository.findById(1)).thenReturn(Optional.of(defaultTier));
        when(plafondRepository.findByLevel(PlafondService.DEFAULT_LEVEL))
                .thenReturn(Optional.of(defaultTier));
        when(plafondRepository
                .findByIsActiveTrueAndMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqual(any(), any()))
                .thenReturn(List.of(defaultTier));

        assertThatThrownBy(() -> service.update(1, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be deactivated");

        verify(plafondRepository, never()).save(any());
    }

    @Test
    @DisplayName("the default tier cannot be deleted either")
    void defaultTierCannotBeDeleted() {
        when(plafondRepository.findById(1))
                .thenReturn(Optional.of(TestFixtures.plafond(PlafondService.DEFAULT_LEVEL, 1L, 10L)));

        assertThatThrownBy(() -> service.delete(1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cannot be deleted");

        verify(plafondRepository, never()).save(any());
    }

    @Test
    @DisplayName("deleting a tier only clears its active flag, so history keeps pointing somewhere")
    void deleteIsASoftDelete() {
        Plafond tier = TestFixtures.plafond(4, 50_000_001L, 80_000_000L);
        when(plafondRepository.findById(4)).thenReturn(Optional.of(tier));

        service.delete(4);

        assertThat(tier.getIsActive()).isFalse();
        verify(plafondRepository).save(tier);
    }

    @Test
    @DisplayName("a customer with no plafond is put on the default one and given its ceiling")
    void assignsDefaultPlafondToNewCustomer() {
        Plafond defaultTier = TestFixtures.plafond(PlafondService.DEFAULT_LEVEL, 1_000_000L, 10_000_000L);
        when(plafondRepository.findByLevel(PlafondService.DEFAULT_LEVEL))
                .thenReturn(Optional.of(defaultTier));

        Customer customer = TestFixtures.customer();
        customer.setPlafond(null);
        customer.setApprovedLimit(null);

        service.assignDefaultPlafond(customer);

        assertThat(customer.getPlafond()).isSameAs(defaultTier);
        assertThat(customer.getApprovedLimit()).isEqualByComparingTo(BigDecimal.valueOf(10_000_000));
    }

    @Test
    @DisplayName("a customer who already holds a plafond keeps it, limit untouched")
    void existingPlafondIsLeftAlone() {
        Customer customer = TestFixtures.customer();
        customer.setApprovedLimit(BigDecimal.valueOf(35_000_000));
        Plafond held = customer.getPlafond();

        service.assignDefaultPlafond(customer);

        assertThat(customer.getPlafond()).isSameAs(held);
        assertThat(customer.getApprovedLimit()).isEqualByComparingTo(BigDecimal.valueOf(35_000_000));
        verify(plafondRepository, never()).findByLevel(any());
    }

    @Test
    @DisplayName("a missing default plafond is a 404, not a silent null")
    void missingDefaultPlafondIsNotFound() {
        when(plafondRepository.findByLevel(PlafondService.DEFAULT_LEVEL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDefaultPlafond())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is not configured");
    }

    @Test
    @DisplayName("an amount resolves to the lowest active tier that covers it")
    void resolvesAmountToTier() {
        Plafond tier = TestFixtures.plafond(2, 10_000_000L, 25_000_000L);
        when(plafondRepository
                .findFirstByIsActiveTrueAndMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqualOrderByLevelAsc(
                        any(), any()))
                .thenReturn(Optional.of(tier));

        assertThat(service.simulate(BigDecimal.valueOf(15_000_000)).getLevel()).isEqualTo(2);
    }

    @Test
    @DisplayName("an amount no tier covers is refused rather than rounded into one")
    void uncoveredAmountIsRefused() {
        when(plafondRepository
                .findFirstByIsActiveTrueAndMinimumAmountLessThanEqualAndMaxAmountGreaterThanEqualOrderByLevelAsc(
                        any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveByAmount(BigDecimal.valueOf(999_000_000)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No plafond level covers the amount")
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
