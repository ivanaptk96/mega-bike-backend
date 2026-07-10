package com.megabike.identity.api;

import java.util.List;

public record CurrentUserResponse(
		String email,
		List<String> authorities
) {
}
