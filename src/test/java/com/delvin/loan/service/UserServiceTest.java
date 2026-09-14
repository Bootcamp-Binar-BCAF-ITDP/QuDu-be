package com.delvin.loan.service;

import com.delvin.loan.common.RoleName;
import com.delvin.loan.dto.request.user.UserRequest;
import com.delvin.loan.dto.response.user.UserResponse;
import com.delvin.loan.model.Branch;
import com.delvin.loan.model.Role;
import com.delvin.loan.model.User;
import com.delvin.loan.repository.BranchRepository;
import com.delvin.loan.repository.RoleRepository;
import com.delvin.loan.repository.UserRepository;
import com.delvin.loan.support.TestFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
class UserServiceTest {

    private static final Pageable PAGE = PageRequest.of(0, 10);

    @Mock
    private UserRepository userRepository;

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private UserService service;

    private UserRequest request;

    @BeforeEach
    void setUp() {
        request = new UserRequest();
        request.setUsername("newname");
        request.setEmail("new@example.com");
        request.setFullName("New Name");
        request.setPhoneNumber("081234567890");
    }

    private User existingUser() {
        User user = TestFixtures.user(RoleName.MARKETING, 3);
        user.setUserId(TestFixtures.USER_ID);
        return user;
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("a blank search becomes a match-everything keyword")
    void blankSearchMatchesEverything(String search) {
        when(userRepository.findAllByStatusIsActive(any(), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(existingUser()), PAGE, 1));

        service.getAllUsers(search, PAGE);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(userRepository).findAllByStatusIsActive(keyword.capture(), eq(PAGE));
        assertThat(keyword.getValue()).isEqualTo("%%");
    }

    @Test
    @DisplayName("a search term is trimmed, lowercased and wrapped for LIKE")
    void searchTermIsNormalised() {
        when(userRepository.findAllByStatusIsActive(any(), eq(PAGE)))
                .thenReturn(new PageImpl<>(List.of(), PAGE, 0));

        service.getAllUsers("  BudI  ", PAGE);

        ArgumentCaptor<String> keyword = ArgumentCaptor.forClass(String.class);
        verify(userRepository).findAllByStatusIsActive(keyword.capture(), eq(PAGE));
        assertThat(keyword.getValue()).isEqualTo("%budi%");
    }

    @Test
    @DisplayName("the response carries the branch and role names, flattened")
    void responseFlattensBranchAndRole() {
        Page<User> page = new PageImpl<>(List.of(existingUser()), PAGE, 1);
        when(userRepository.findAllByStatusIsActive(any(), eq(PAGE))).thenReturn(page);

        UserResponse response = service.getAllUsers(null, PAGE).getContent().get(0);

        assertThat(response.getBranchId()).isEqualTo(3);
        assertThat(response.getBranchName()).isEqualTo("Branch 3");
        assertThat(response.getRoleName()).isEqualTo(RoleName.MARKETING);
    }

    @Test
    @DisplayName("a user with no branch or role still maps, leaving those fields empty")
    void mapsUserWithoutBranchOrRole() {
        User bare = new User();
        bare.setUserId("USR-BARE");
        bare.setUsername("bare");

        when(userRepository.findById("USR-BARE")).thenReturn(Optional.of(bare));

        UserResponse response = service.getUserById("USR-BARE");

        assertThat(response.getUserId()).isEqualTo("USR-BARE");
        assertThat(response.getBranchId()).isNull();
        assertThat(response.getRoleId()).isNull();
    }

    @Test
    @DisplayName("an unknown user id is refused")
    void unknownUserIdIsRefused() {
        when(userRepository.findById("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getUserById("nobody"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("an update writes the plain fields")
    void updateWritesPlainFields() {
        User existing = existingUser();
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.updateUser(TestFixtures.USER_ID, request);

        assertThat(existing.getUsername()).isEqualTo("newname");
        assertThat(existing.getEmail()).isEqualTo("new@example.com");
        assertThat(existing.getFullName()).isEqualTo("New Name");
        assertThat(existing.getPhoneNumber()).isEqualTo("081234567890");
    }

    @Test
    @DisplayName("omitting branch, role and the active flag leaves all three untouched")
    void omittedFieldsAreLeftAlone() {
        User existing = existingUser();
        Branch originalBranch = existing.getBranch();
        Role originalRole = existing.getRole();

        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.updateUser(TestFixtures.USER_ID, request);

        assertThat(existing.getBranch()).isSameAs(originalBranch);
        assertThat(existing.getRole()).isSameAs(originalRole);
        assertThat(existing.getIsActive()).isTrue();
        verify(branchRepository, never()).findByBranchIdAndIsActive(any(), any());
        verify(roleRepository, never()).findById(any());
    }

    @Test
    @DisplayName("a supplied branch id moves the user to that branch")
    void updateMovesUserToBranch() {
        request.setBranchId(9);

        User existing = existingUser();
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(existing));
        when(branchRepository.findByBranchIdAndIsActive(9, true))
                .thenReturn(Optional.of(TestFixtures.branch(9)));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.updateUser(TestFixtures.USER_ID, request);

        assertThat(existing.getBranch().getBranchId()).isEqualTo(9);
    }

    @Test
    @DisplayName("an unknown branch is refused before the user is saved")
    void updateRejectsUnknownBranch() {
        request.setBranchId(404);

        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(existingUser()));
        when(branchRepository.findByBranchIdAndIsActive(404, true)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUser(TestFixtures.USER_ID, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Branch not found");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("a supplied role id changes the user's role")
    void updateChangesRole() {
        request.setRoleId(4);

        Role role = new Role();
        role.setRoleId(4);
        role.setRoleName(RoleName.BACK_OFFICE);

        User existing = existingUser();
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(existing));
        when(roleRepository.findById(4)).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.updateUser(TestFixtures.USER_ID, request);

        assertThat(existing.getRole().getRoleName()).isEqualTo(RoleName.BACK_OFFICE);
    }

    @Test
    @DisplayName("an unknown role is refused before the user is saved")
    void updateRejectsUnknownRole() {
        request.setRoleId(404);

        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(existingUser()));
        when(roleRepository.findById(404)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUser(TestFixtures.USER_ID, request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Role not found");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("the active flag is written when the request carries one")
    void updateWritesActiveFlagWhenSupplied() {
        request.setIsActive(false);

        User existing = existingUser();
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(existing));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        service.updateUser(TestFixtures.USER_ID, request);

        assertThat(existing.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("updating an unknown user is refused")
    void updateUnknownUserIsRefused() {
        when(userRepository.findById("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateUser("nobody", request))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");
    }

    @Test
    @DisplayName("deleting a user only clears the active flag, the row survives")
    void deleteIsASoftDelete() {
        User existing = existingUser();
        when(userRepository.findById(TestFixtures.USER_ID)).thenReturn(Optional.of(existing));

        service.deleteUser(TestFixtures.USER_ID);

        assertThat(existing.getIsActive()).isFalse();
        verify(userRepository).save(existing);
    }

    @Test
    @DisplayName("deleting an unknown user is refused")
    void deleteUnknownUserIsRefused() {
        when(userRepository.findById("nobody")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteUser("nobody"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("User not found");

        verify(userRepository, never()).save(any());
    }
}
