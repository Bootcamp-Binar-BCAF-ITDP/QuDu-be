package com.delvin.loan.controller;

import com.delvin.loan.common.ApiResponse;
import com.delvin.loan.common.PageResponse;
import com.delvin.loan.dto.request.user.UserRequest;
import com.delvin.loan.dto.response.user.UserResponse;
import com.delvin.loan.service.UserService;
import com.delvin.loan.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController controller;

    private UserResponse user() {
        UserResponse response = new UserResponse();
        response.setUserId(TestFixtures.USER_ID);
        response.setUsername("staff");
        return response;
    }

    @Test
    @DisplayName("a populated list is returned with the success message")
    void listReturnsUsers() {
        when(userService.getAllUsers(eq(""), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(user()), 0, 10, 1, 1, true, true, false));

        ResponseEntity<ApiResponse<PageResponse<UserResponse>>> result =
                controller.getAllUsers(0, 10, "username", "asc", "");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("Users retrieved successfully");
    }

    @Test
    @DisplayName("an empty result with no search term says there is no data at all")
    void emptyWithoutSearchSaysNoData() {
        when(userService.getAllUsers(eq(""), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true));

        assertThat(controller.getAllUsers(0, 10, "username", "asc", "").getBody().getMessage())
                .isEqualTo("No user data found");
    }

    @Test
    @DisplayName("an empty result with a search term blames the search")
    void emptyWithSearchBlamesTheSearch() {
        when(userService.getAllUsers(eq("zzz"), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(), 0, 10, 0, 0, true, true, true));

        assertThat(controller.getAllUsers(0, 10, "username", "asc", "zzz").getBody().getMessage())
                .isEqualTo("No user matches your search");
    }

    @Test
    @DisplayName("a user is returned by id")
    void getByIdReturnsUser() {
        when(userService.getUserById(TestFixtures.USER_ID)).thenReturn(user());

        ResponseEntity<ApiResponse<UserResponse>> result =
                controller.getUserById(TestFixtures.USER_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("User found");
        assertThat(result.getBody().getData().getUsername()).isEqualTo("staff");
    }

    @Test
    @DisplayName("a missing user becomes a 404 body")
    void missingUserBecomes404() {
        when(userService.getUserById("nobody")).thenThrow(new RuntimeException("User not found"));

        ResponseEntity<ApiResponse<UserResponse>> result = controller.getUserById("nobody");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(result.getBody().getMessage()).isEqualTo("User not found");
    }

    @Test
    @DisplayName("an update answers 200")
    void updateReturns200() {
        UserRequest request = new UserRequest();
        when(userService.updateUser(TestFixtures.USER_ID, request)).thenReturn(user());

        ResponseEntity<ApiResponse<UserResponse>> result =
                controller.updateUser(TestFixtures.USER_ID, request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("User updated successfully");
    }

    @Test
    @DisplayName("updating a missing user answers 404")
    void updateMissingBecomes404() {
        UserRequest request = new UserRequest();
        when(userService.updateUser("nobody", request))
                .thenThrow(new RuntimeException("User not found"));

        assertThat(controller.updateUser("nobody", request).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("a delete answers 200 and says deactivated, because the row survives")
    void deleteSaysDeactivated() {
        ResponseEntity<ApiResponse<Object>> result = controller.deleteUser(TestFixtures.USER_ID);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody().getMessage()).isEqualTo("User deactivated successfully");
    }

    @Test
    @DisplayName("deleting a missing user answers 404")
    void deleteMissingBecomes404() {
        doThrow(new RuntimeException("User not found")).when(userService).deleteUser("nobody");

        assertThat(controller.deleteUser("nobody").getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
