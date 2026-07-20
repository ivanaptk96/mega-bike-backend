package com.megabike.identity.application;

import com.megabike.identity.api.LoginRequest;
import com.megabike.identity.api.LoginResponse;
import com.megabike.identity.api.CurrentUserResponse;
import com.megabike.identity.api.LogoutRequest;
import com.megabike.identity.api.LogoutResponse;
import com.megabike.identity.api.RefreshTokenRequest;
import com.megabike.identity.api.TokenResponse;
import com.megabike.identity.domain.Permission;
import com.megabike.identity.domain.RefreshToken;
import com.megabike.identity.domain.Role;
import com.megabike.identity.domain.UserAccount;
import com.megabike.identity.domain.UserAccountRepository;
import com.megabike.identity.infrastructure.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AuthService {

	private static final String TOKEN_TYPE = "Bearer";

	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final UserAccountRepository userAccountRepository;

	public AuthService(
			AuthenticationManager authenticationManager,
			JwtService jwtService,
			RefreshTokenService refreshTokenService,
			UserAccountRepository userAccountRepository
	) {
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
		this.userAccountRepository = userAccountRepository;
	}

	@Transactional
	public LoginResponse login(LoginRequest request) {
		/*
		 * AuthenticationManager is Spring Security's login coordinator.
		 * It calls our JpaUserDetailsService to load the user and uses the configured
		 * PasswordEncoder to compare the raw password with the BCrypt password hash.
		 */
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password())
		);

		UserDetails userDetails = (UserDetails) authentication.getPrincipal();
		JwtService.GeneratedToken token = jwtService.createAccessToken(userDetails);
		UserAccount userAccount = userAccountRepository.findWithRolesAndPermissionsByEmail(userDetails.getUsername())
				.orElseThrow(() -> new BadCredentialsException("Authenticated user no longer exists."));
		RefreshTokenService.CreatedRefreshToken refreshToken = refreshTokenService.create(userAccount);

		return new LoginResponse(
				token.value(),
				refreshToken.value(),
				TOKEN_TYPE,
				token.expiresInSeconds(),
				token.expiresAt(),
				refreshToken.expiresAt(),
				userDetails.getUsername(),
				authorities(userDetails)
		);
	}

	@Transactional(readOnly = true)
	public TokenResponse refresh(RefreshTokenRequest request) {
		RefreshToken refreshToken = refreshTokenService.findUsable(request.refreshToken())
				.orElseThrow(() -> new BadCredentialsException("Invalid refresh token."));

		UserAccount userAccount = userAccountRepository.findWithRolesAndPermissionsByEmail(refreshToken.getUserAccount().getEmail())
				.orElseThrow(() -> new BadCredentialsException("Refresh token user no longer exists."));

		if (!userAccount.isEnabled()) {
			throw new BadCredentialsException("Refresh token user is disabled.");
		}

		JwtService.GeneratedToken token = jwtService.createAccessToken(toUserDetails(userAccount));
		return new TokenResponse(
				token.value(),
				TOKEN_TYPE,
				token.expiresInSeconds(),
				token.expiresAt()
		);
	}

	@Transactional
	public LogoutResponse logout(LogoutRequest request) {
		return new LogoutResponse(refreshTokenService.revoke(request.refreshToken()));
	}

	public CurrentUserResponse currentUser(Authentication authentication) {
		return new CurrentUserResponse(
				authentication.getName(),
				authentication.getAuthorities().stream()
						.map(GrantedAuthority::getAuthority)
						.sorted()
						.toList()
		);
	}

	private List<String> authorities(UserDetails userDetails) {
		return userDetails.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.sorted()
				.toList();
	}

	private UserDetails toUserDetails(UserAccount userAccount) {
		return User.withUsername(userAccount.getEmail())
				.password(userAccount.getPasswordHash())
				.disabled(!userAccount.isEnabled())
				.authorities(toAuthorities(userAccount))
				.build();
	}

	private Set<GrantedAuthority> toAuthorities(UserAccount userAccount) {
		Set<GrantedAuthority> authorities = new LinkedHashSet<>();

		for (Role role : userAccount.getRoles()) {
			authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
			for (Permission permission : role.getPermissions()) {
				authorities.add(new SimpleGrantedAuthority(permission.getName()));
			}
		}

		return authorities;
	}
}
