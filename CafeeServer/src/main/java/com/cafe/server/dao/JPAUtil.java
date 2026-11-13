package com.cafe.server.dao;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.Map;

public class JPAUtil {
	private static volatile EntityManagerFactory factory;

	public static EntityManagerFactory getEntityManagerFactory() {
		EntityManagerFactory current = factory;
		if (current == null || !current.isOpen()) {
			synchronized (JPAUtil.class) {
				if (factory == null || !factory.isOpen()) {
					factory = Persistence.createEntityManagerFactory("CafePU");
				}
				current = factory;
			}
		}
		return current;
	}

	public static synchronized void reinitializeWithSchemaUpdate() {
		closeEntityManagerFactory();
		factory = Persistence.createEntityManagerFactory("CafePU",
				Map.of("hibernate.hbm2ddl.auto", "update"));
	}

	public static synchronized void setEntityManagerFactory(EntityManagerFactory customFactory) {
		closeEntityManagerFactory();
		factory = customFactory;
	}

	public static synchronized void closeEntityManagerFactory() {
		if (factory != null && factory.isOpen()) {
			factory.close();
		}
		factory = null;
	}
}
