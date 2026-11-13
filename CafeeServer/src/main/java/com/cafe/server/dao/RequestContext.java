package com.cafe.server.dao;

import jakarta.persistence.EntityManager;

/**
 * Ngữ cảnh deadline theo luồng xử lý Request để ngăn commit giao dịch sau khi Client đã timeout.
 */
public final class RequestContext {

	private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

	private RequestContext() {
	}

	public static void setDeadlineMs(long timeoutMs) {
		if (timeoutMs <= 0) {
			DEADLINE_NANOS.remove();
			return;
		}
		DEADLINE_NANOS.set(System.nanoTime() + timeoutMs * 1_000_000L);
	}

	public static boolean isExpired() {
		Long deadline = DEADLINE_NANOS.get();
		return deadline != null && System.nanoTime() >= deadline;
	}

	public static long remainingMs() {
		Long deadline = DEADLINE_NANOS.get();
		if (deadline == null) {
			return Long.MAX_VALUE;
		}
		long diff = deadline - System.nanoTime();
		return diff <= 0 ? 0L : diff / 1_000_000L;
	}

	/**
	 * Kiểm tra deadline ngay trước khi commit; nếu đã hết hạn thì rollback giao dịch để Server không commit trễ.
	 */
	public static boolean rollbackIfExpired(EntityManager em) {
		if (!isExpired()) {
			return false;
		}
		if (em != null && em.getTransaction().isActive()) {
			em.getTransaction().rollback();
		}
		return true;
	}

	public static void clear() {
		DEADLINE_NANOS.remove();
	}
}
