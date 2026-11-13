package com.cafe.server.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * Tiện ích băm và xác thực mật khẩu sử dụng PBKDF2WithHmacSHA256 chuẩn JDK.
 * Hỗ trợ tương thích ngược với dữ liệu plain-text cũ trong CafeDB để tự nâng cấp khi đăng nhập.
 */
public final class PasswordUtil {
	private static final String PREFIX = "PBKDF2$";
	private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
	private static final int ITERATIONS = 65536;
	private static final int SALT_BYTES = 16;
	private static final int KEY_BITS = 256;
	private static final SecureRandom RANDOM = new SecureRandom();

	private PasswordUtil() {
	}

	public static String hashPassword(String rawPassword) {
		if (rawPassword == null || rawPassword.isEmpty()) {
			throw new IllegalArgumentException("Mật khẩu không được để trống");
		}
		byte[] salt = new byte[SALT_BYTES];
		RANDOM.nextBytes(salt);
		byte[] hash = pbkdf2(rawPassword.toCharArray(), salt);
		return PREFIX
				+ Base64.getEncoder().encodeToString(salt)
				+ "$"
				+ Base64.getEncoder().encodeToString(hash);
	}

	public static boolean verifyPassword(String rawPassword, String storedPassword) {
		if (rawPassword == null || storedPassword == null) {
			return false;
		}
		if (!storedPassword.startsWith(PREFIX)) {
			// Tương thích dữ liệu plain-text cũ chưa được băm
			return MessageDigest.isEqual(
					rawPassword.getBytes(StandardCharsets.UTF_8),
					storedPassword.getBytes(StandardCharsets.UTF_8));
		}

		String[] parts = storedPassword.substring(PREFIX.length()).split("\\$");
		if (parts.length != 2) {
			return false;
		}
		try {
			byte[] salt = Base64.getDecoder().decode(parts[0]);
			byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
			byte[] actualHash = pbkdf2(rawPassword.toCharArray(), salt);
			return MessageDigest.isEqual(expectedHash, actualHash);
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	public static boolean needsRehash(String storedPassword) {
		return storedPassword == null || !storedPassword.startsWith(PREFIX);
	}

	private static byte[] pbkdf2(char[] password, byte[] salt) {
		PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
		try {
			SecretKeyFactory skf = SecretKeyFactory.getInstance(ALGORITHM);
			return skf.generateSecret(spec).getEncoded();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new IllegalStateException("Không thể khởi tạo thuật toán băm mật khẩu", e);
		} finally {
			spec.clearPassword();
		}
	}
}
