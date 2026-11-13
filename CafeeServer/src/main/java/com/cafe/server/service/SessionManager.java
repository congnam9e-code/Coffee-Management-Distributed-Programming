package com.cafe.server.service;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import com.cafe.common.entity.Employee;
import com.cafe.common.network.Response;
import com.cafe.server.dao.RequestContext;

/**
 * Quản lý phiên đăng nhập theo token, cập nhật quyền trực tiếp và chống ghi trùng (idempotency) theo requestId.
 */
public final class SessionManager {

	private static final int MAX_CACHED_REQUESTS_PER_SESSION = 64;

	private static final class SessionEntry {
		private volatile Employee user;
		private final Map<String, Response> responseCache = Collections.synchronizedMap(
				new LinkedHashMap<String, Response>(MAX_CACHED_REQUESTS_PER_SESSION, 0.75f, true) {
					private static final long serialVersionUID = 1L;

					@Override
					protected boolean removeEldestEntry(Map.Entry<String, Response> eldest) {
						return size() > MAX_CACHED_REQUESTS_PER_SESSION;
					}
				});
		private final ConcurrentHashMap<String, CompletableFuture<Response>> inFlight = new ConcurrentHashMap<>();

		private SessionEntry(Employee user) {
			this.user = user;
		}
	}

	private static final ConcurrentHashMap<String, SessionEntry> SESSIONS = new ConcurrentHashMap<>();

	private SessionManager() {
	}

	public static String createSession(Employee user) {
		if (user == null) {
			return null;
		}
		String token = UUID.randomUUID().toString();
		SESSIONS.put(token, new SessionEntry(sanitizeCopy(user)));
		return token;
	}

	public static Employee getUser(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		SessionEntry entry = SESSIONS.get(token);
		return entry != null ? entry.user : null;
	}

	public static void invalidate(String token) {
		if (token != null && !token.isBlank()) {
			SESSIONS.remove(token);
		}
	}

	public static Set<String> invalidateUser(int employeeId) {
		Set<String> removedTokens = new HashSet<>();
		for (Map.Entry<String, SessionEntry> entry : SESSIONS.entrySet()) {
			Employee u = entry.getValue().user;
			if (u != null && u.getId() == employeeId) {
				if (SESSIONS.remove(entry.getKey(), entry.getValue())) {
					removedTokens.add(entry.getKey());
				}
			}
		}
		return removedTokens;
	}

	public static void refreshUser(Employee updated) {
		if (updated == null || updated.getId() <= 0) {
			return;
		}
		Employee sanitized = sanitizeCopy(updated);
		for (SessionEntry entry : SESSIONS.values()) {
			Employee current = entry.user;
			if (current != null && current.getId() == sanitized.getId()) {
				entry.user = sanitized;
			}
		}
	}

	/**
	 * Thực thi idempotent theo (sessionToken, requestId): trả lại kết quả cũ hoặc chờ request đang chạy cùng ID.
	 */
	public static Response executeIdempotent(String token, String requestId, Supplier<Response> executor) {
		if (token == null || token.isBlank() || requestId == null || requestId.isBlank()) {
			return executor.get();
		}
		SessionEntry entry = SESSIONS.get(token);
		if (entry == null) {
			return executor.get();
		}

		Response cached = entry.responseCache.get(requestId);
		if (cached != null) {
			return cached;
		}

		CompletableFuture<Response> mine = new CompletableFuture<>();
		CompletableFuture<Response> existing = entry.inFlight.putIfAbsent(requestId, mine);
		if (existing != null) {
			try {
				long waitMs = Math.max(1000L, Math.min(16000L, RequestContext.remainingMs()));
				return existing.get(waitMs, TimeUnit.MILLISECONDS);
			} catch (Exception e) {
				Response rechecked = entry.responseCache.get(requestId);
				if (rechecked != null) {
					return rechecked;
				}
				return new Response(false, "Yêu cầu đang được xử lý quá lâu hoặc bị gián đoạn!", null);
			}
		}

		Response result = null;
		try {
			// Double-check sau khi giữ slot inFlight để tránh race giữa lúc hoàn tất và putIfAbsent
			Response again = entry.responseCache.get(requestId);
			if (again != null) {
				result = again;
				return again;
			}
			result = executor.get();
			if (result != null && !RequestContext.isExpired()) {
				entry.responseCache.put(requestId, result);
			}
			mine.complete(result);
			return result;
		} catch (Throwable t) {
			mine.completeExceptionally(t);
			throw t;
		} finally {
			if (!mine.isDone()) {
				mine.complete(result);
			}
			entry.inFlight.remove(requestId, mine);
		}
	}

	public static void clearAll() {
		SESSIONS.clear();
	}

	private static Employee sanitizeCopy(Employee user) {
		Employee copy = new Employee();
		copy.setId(user.getId());
		copy.setUsername(user.getUsername());
		copy.setPassword(null);
		copy.setFullName(user.getFullName());
		copy.setRole(user.getRole());
		copy.setActive(user.isActive());
		return copy;
	}
}
