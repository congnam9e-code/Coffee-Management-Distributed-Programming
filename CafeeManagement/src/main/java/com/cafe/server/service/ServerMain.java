package com.cafe.server.service;

import java.net.ServerSocket;
import java.net.Socket;
import jakarta.persistence.EntityManager;
import com.cafe.common.entity.Category;
import com.cafe.common.entity.CoffeeTable;
import com.cafe.common.entity.Employee;
import com.cafe.common.entity.Product;
import com.cafe.server.dao.JPAUtil;

public class ServerMain {
	public static void main(String[] args) {
		int port = com.cafe.common.network.Config.SERVER_PORT;

		// Khởi tạo JPA trước để test kết nối CSDL và tạo bảng
		System.out.println("Initializing JPA EntityManagerFactory...");
		try {
			JPAUtil.getEntityManagerFactory();
			System.out.println("JPA Connected to SQL Server! Database schemas are updated.");
			// Tự động kiểm tra và thêm dữ liệu khởi tạo mẫu nếu database còn trống
			initDefaultData();
		} catch (Exception e) {
			System.err.println("Failed to connect to Database. Check persistence.xml credentials!");
			e.printStackTrace();
			return;
		}

		try (ServerSocket serverSocket = new ServerSocket(port)) {
			System.out.println("Cafe Server is running on port " + port);

			while (true) {
				Socket clientSocket = serverSocket.accept();
				System.out.println("New client connected: " + clientSocket.getInetAddress());

				ClientHandler handler = new ClientHandler(clientSocket);
				new Thread(handler).start();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void initDefaultData() {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();

			// 1. Khởi tạo tài khoản Quản lý & Nhân viên mẫu nếu chưa có
			Long empCount = em.createQuery("SELECT COUNT(e) FROM Employee e", Long.class).getSingleResult();
			if (empCount == 0) {
				Employee admin = new Employee();
				admin.setUsername("admin");
				admin.setPassword("123456");
				admin.setFullName("Quản Trị Viên");
				admin.setRole("MANAGER");
				em.persist(admin);

				Employee staff = new Employee();
				staff.setUsername("staff");
				staff.setPassword("123456");
				staff.setFullName("Nhân Viên Bán Hàng");
				staff.setRole("STAFF");
				em.persist(staff);
				System.out.println("[Seeder] Created default accounts: admin (MANAGER), staff (STAFF) - Password: 123456");
			}

			// 2. Khởi tạo danh sách bàn mẫu nếu chưa có
			Long tableCount = em.createQuery("SELECT COUNT(t) FROM CoffeeTable t", Long.class).getSingleResult();
			if (tableCount == 0) {
				for (int i = 1; i <= 12; i++) {
					CoffeeTable t = new CoffeeTable();
					t.setName("Bàn " + (i < 10 ? "0" + i : i));
					t.setStatus("AVAILABLE");
					em.persist(t);
				}
				System.out.println("[Seeder] Created 12 default coffee tables (Bàn 01 -> Bàn 12).");
			}

			// 3. Khởi tạo danh mục và sản phẩm mẫu nếu chưa có
			Long catCount = em.createQuery("SELECT COUNT(c) FROM Category c", Long.class).getSingleResult();
			if (catCount == 0) {
				Category catCafe = new Category();
				catCafe.setName("Cà Phê");
				em.persist(catCafe);

				Category catTea = new Category();
				catTea.setName("Trà & Trà Sữa");
				em.persist(catTea);

				Category catCake = new Category();
				catCake.setName("Bánh Ngọt");
				em.persist(catCake);

				Product p1 = new Product();
				p1.setName("Cà phê đen đá");
				p1.setPrice(20000);
				p1.setCategory(catCafe);
				em.persist(p1);

				Product p2 = new Product();
				p2.setName("Cà phê sữa đá");
				p2.setPrice(25000);
				p2.setCategory(catCafe);
				em.persist(p2);

				Product p3 = new Product();
				p3.setName("Trà đào cam sả");
				p3.setPrice(35000);
				p3.setCategory(catTea);
				em.persist(p3);

				Product p4 = new Product();
				p4.setName("Trà sữa trân châu");
				p4.setPrice(30000);
				p4.setCategory(catTea);
				em.persist(p4);

				Product p5 = new Product();
				p5.setName("Bánh Tiramisu");
				p5.setPrice(40000);
				p5.setCategory(catCake);
				em.persist(p5);
				System.out.println("[Seeder] Created sample categories and products.");
			}

			em.getTransaction().commit();
		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			System.err.println("[Seeder] Error initializing default data: " + e.getMessage());
		} finally {
			em.close();
		}
	}
}
