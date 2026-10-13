package com.megabike.sef.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class SefPdfContent {

	private SefPdfContent() {}

	public static boolean isPdf(byte[] content) {
		return content != null && content.length >= 5
				&& new String(content, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-");
	}

	public static String sha256(byte[] content) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}
}
