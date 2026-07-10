package com.megabike.identity.infrastructure;

import com.megabike.identity.domain.Permission;
import com.megabike.identity.domain.Role;
import com.megabike.identity.domain.UserAccount;
import com.megabike.identity.domain.UserAccountRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
public class JpaUserDetailsService implements UserDetailsService {

	private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

	private final UserAccountRepository userAccountRepository;

	public JpaUserDetailsService(UserAccountRepository userAccountRepository) {
		this.userAccountRepository = userAccountRepository;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) {
		UserAccount userAccount = userAccountRepository.findWithRolesAndPermissionsByEmail(username)
				.orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

		return User.withUsername(userAccount.getEmail())
				.password(userAccount.getPasswordHash())
				.disabled(!userAccount.isEnabled())
				.authorities(toAuthorities(userAccount))
				.build();
	}

	private Set<GrantedAuthority> toAuthorities(UserAccount userAccount) {
		Set<GrantedAuthority> authorities = new LinkedHashSet<>();

		for (Role role : userAccount.getRoles()) {
			authorities.add(new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + role.getName()));
			for (Permission permission : role.getPermissions()) {
				authorities.add(new SimpleGrantedAuthority(permission.getName()));
			}
		}

		return authorities;
	}
}
