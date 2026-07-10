package com.megabike.identity.application;

import com.megabike.identity.api.LoginRequest;
import com.megabike.identity.api.LoginResponse;
import com.megabike.identity.infrastructure.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthService {

	private static final String TOKEN_TYPE = "Bearer";

	private final AuthenticationManager authenticationManager;
	private final JwtService jwtService;

	public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
		this.authenticationManager = authenticationManager;
		this.jwtService = jwtService;
	}

	public LoginResponse login(LoginRequest request) {
		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password())
		);

		UserDetails userDetails = (UserDetails) authentication.getPrincipal();
		JwtService.GeneratedToken token = jwtService.createAccessToken(userDetails);

		return new LoginResponse(
				token.value(),
				TOKEN_TYPE,
				token.expiresInSeconds(),
				token.expiresAt(),
				userDetails.getUsername(),
				authorities(userDetails)
		);
	}

	private List<String> authorities(UserDetails userDetails) {
		return userDetails.getAuthorities().stream()
				.map(GrantedAuthority::getAuthority)
				.sorted()
				.toList();
	}
}
