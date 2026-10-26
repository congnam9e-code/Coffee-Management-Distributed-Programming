package com.cafe.server.dao;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class JPAUtil {
	private static EntityManagerFactory factory;

	public static EntityManagerFactory getEntityManagerFactory() {
		if (factory == null) {
			factory = Persistence.createEntityManagerFactory("CafePU");
		}
		return factory;
	}
}
