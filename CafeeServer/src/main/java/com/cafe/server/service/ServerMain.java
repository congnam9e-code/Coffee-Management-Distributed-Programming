package com.cafe.server.service;

import java.math.BigDecimal;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.persistence.EntityManager;
import com.cafe.common.entity.Bill;
import com.cafe.common.entity.BillDetail;
import com.cafe.common.entity.Category;
import com.cafe.common.entity.CoffeeTable;
import com.cafe.common.entity.Employee;
import com.cafe.common.entity.Product;
import com.cafe.common.network.Config;
import com.cafe.server.dao.JPAUtil;
import com.cafe.server.util.PasswordUtil;

public class ServerMain {
	private static final int MAX_CLIENT_THREADS = 100;

	public static void main(String[] args) {
		int port = Config.SERVER_PORT;
		ExecutorService clientPool = Executors.newFixedThreadPool(MAX_CLIENT_THREADS);
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			SyncBroadcaster.shutdown();
			clientPool.shutdownNow();
			JPAUtil.closeEntityManagerFactory();
			System.out.println("Cafe Server stopped.");
		}));

		try (ServerSocket serverSocket = new ServerSocket(port)) {
			System.out.println("Initializing JPA EntityManagerFactory...");
			long startMs = System.currentTimeMillis();
			if (!initializeDatabaseWithRetry()) {
				return;
			}
			System.out.println("JPA Connected to SQL Server in " + (System.currentTimeMillis() - startMs) + " ms.");

			System.out.println("Cafe Server is running on port " + port);
			while (true) {
				Socket clientSocket = serverSocket.accept();
				clientSocket.setTcpNoDelay(true);
				clientSocket.setKeepAlive(true);
				System.out.println("New client connected: " + clientSocket.getInetAddress());
				clientPool.execute(new ClientHandler(clientSocket));
			}
		} catch (BindException e) {
			System.err.println("ERROR: Port " + port + " is already in use! Please stop any old ServerMain process first.");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SyncBroadcaster.shutdown();
			clientPool.shutdown();
			JPAUtil.closeEntityManagerFactory();
		}
	}

	public static boolean initializeDatabaseWithRetry() {
		int maxAttempts = 3;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				JPAUtil.getEntityManagerFactory();
				initDefaultData();
				return true;
			} catch (Exception e) {
				if (attempt < maxAttempts) {
					System.err.println("[DB] Lan ket noi #" + attempt + " bi gian doan (" + e.getMessage()
							+ "). Dang thu ket noi lai sau 2 giay...");
					JPAUtil.closeEntityManagerFactory();
					try {
						Thread.sleep(2000);
					} catch (InterruptedException ie) {
						Thread.currentThread().interrupt();
						return false;
					}
				} else {
					System.err.println("Failed to connect to Database. Check persistence.xml credentials!");
					e.printStackTrace();
					return false;
				}
			}
		}
		return false;
	}

	public static void initDefaultData() {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			Long empCount;
			try {
				empCount = em.createQuery("SELECT COUNT(e) FROM Employee e", Long.class).getSingleResult();
			} catch (Exception schemaEx) {
				em.close();
				System.out.println("[Schema] Database tables missing, initializing schema with hbm2ddl.auto=update...");
				JPAUtil.reinitializeWithSchemaUpdate();
				em = JPAUtil.getEntityManagerFactory().createEntityManager();
				empCount = em.createQuery("SELECT COUNT(e) FROM Employee e", Long.class).getSingleResult();
			}

			if (empCount > 0) {
				return;
			}

			em.getTransaction().begin();
			Long tableCount = em.createQuery("SELECT COUNT(t) FROM CoffeeTable t", Long.class).getSingleResult();
			Long catCount = em.createQuery("SELECT COUNT(c) FROM Category c", Long.class).getSingleResult();

			// Chỉ khởi tạo dữ liệu mẫu khi CSDL hoàn toàn trống để không bao giờ ghi đè/xóa dữ liệu thực tế
			if (empCount == 0 && tableCount == 0 && catCount == 0) {
				System.out.println("[Seeder] CSDL dang trong. Dang khoi tao bo du lieu mau...");

				String defaultPassHash = PasswordUtil.hashPassword("123456");

				// 1. Tạo 6 tài khoản nhân sự (mật khẩu mặc định đã băm PBKDF2: 123456)
				Employee admin = createEmp("admin", defaultPassHash, "Nguyễn Võ Đức (Admin)", "MANAGER", em);
				createEmp("nhan_ql", defaultPassHash, "Nguyễn Thành Nhân (Quản Lý)", "MANAGER", em);
				Employee staff1 = createEmp("staff", defaultPassHash, "Nhân Viên Bán Hàng 01", "STAFF", em);
				createEmp("lan_barista", defaultPassHash, "Trần Hoàng Lan (Pha Chế)", "STAFF", em);
				Employee staff3 = createEmp("khoa_order", defaultPassHash, "Lê Đăng Khoa (Phục Vụ)", "STAFF", em);
				Employee staff4 = createEmp("vy_cashier", defaultPassHash, "Phạm Thảo Vy (Thu Ngân)", "STAFF", em);

				// Giữ tham chiếu tránh cảnh báo biến chưa dùng
				if (admin == null) {
					throw new IllegalStateException("Seeder failed");
				}

				// 2. Tạo 16 bàn cafe (Bàn 02 và 04 có khách OCCUPIED, còn lại AVAILABLE)
				List<CoffeeTable> tables = new ArrayList<>();
				for (int i = 1; i <= 16; i++) {
					CoffeeTable t = new CoffeeTable();
					t.setName("Bàn " + (i < 10 ? "0" + i : i));
					t.setStatus((i == 2 || i == 4) ? "OCCUPIED" : "AVAILABLE");
					em.persist(t);
					tables.add(t);
				}

				// 3. Tạo 6 Danh mục
				Category c1 = createCat("Cà Phê Truyền Thống", em);
				Category c2 = createCat("Cà Phê Pha Máy (Espresso)", em);
				Category c3 = createCat("Trà Trái Cây Thanh Nhiệt", em);
				Category c4 = createCat("Trà Sữa & Macchiato", em);
				Category c5 = createCat("Đá Xay & Sinh Tố", em);
				Category c6 = createCat("Bánh Ngọt & Tráng Miệng", em);

				// 4. Tạo 24 Món ăn & Nước uống
				List<Product> products = new ArrayList<>();
				products.add(createProd("Cà phê đen đá", 20000, c1, em));
				products.add(createProd("Cà phê sữa đá", 25000, c1, em));
				products.add(createProd("Bạc xỉu 3 tầng", 28000, c1, em));
				products.add(createProd("Cà phê muối xứ Huế", 32000, c1, em));
				products.add(createProd("Cà phê cốt dừa Hải Phòng", 35000, c1, em));

				products.add(createProd("Espresso Single", 25000, c2, em));
				products.add(createProd("Americano đá", 30000, c2, em));
				products.add(createProd("Cappuccino nóng", 38000, c2, em));
				products.add(createProd("Latte Macchiato", 42000, c2, em));
				products.add(createProd("Caramel Macchiato", 45000, c2, em));

				products.add(createProd("Trà đào cam sả", 35000, c3, em));
				products.add(createProd("Trà vải hoa hồng", 35000, c3, em));
				products.add(createProd("Trà ổi hồng chanh leo", 38000, c3, em));
				products.add(createProd("Trà sen vàng kem cheese", 40000, c3, em));

				products.add(createProd("Trà sữa trân châu hoàng kim", 32000, c4, em));
				products.add(createProd("Trà sữa Oolong nướng", 35000, c4, em));
				products.add(createProd("Trà sữa matcha kem mặn", 40000, c4, em));

				products.add(createProd("Freeze sô-cô-la hạnh nhân", 45000, c5, em));
				products.add(createProd("Matcha đá xay kem tươi", 48000, c5, em));
				products.add(createProd("Sinh tố bơ dừa Bến Tre", 40000, c5, em));
				products.add(createProd("Sinh tố xoài chanh dây", 38000, c5, em));

				products.add(createProd("Bánh Tiramisu Ý", 40000, c6, em));
				products.add(createProd("Bánh Mousse chanh dây", 38000, c6, em));
				products.add(createProd("Bánh Croissant bơ tỏi", 30000, c6, em));

				// 5. Tạo các hóa đơn lịch sử đã thanh toán (PAID)
				Calendar cal = Calendar.getInstance();

				cal.add(Calendar.HOUR_OF_DAY, -4);
				Date in1 = cal.getTime();
				cal.add(Calendar.MINUTE, 45);
				Date out1 = cal.getTime();
				createBill(tables.get(0), staff1, in1, out1, "PAID",
						List.of(new DetailItem(products.get(1), 2, 25000)), em);

				cal.add(Calendar.HOUR_OF_DAY, -3);
				Date in2 = cal.getTime();
				cal.add(Calendar.MINUTE, 50);
				Date out2 = cal.getTime();
				createBill(tables.get(2), staff1, in2, out2, "PAID",
						List.of(new DetailItem(products.get(6), 2, 30000), new DetailItem(products.get(22), 1, 38000)), em);

				cal.add(Calendar.HOUR_OF_DAY, -2);
				Date in3 = cal.getTime();
				cal.add(Calendar.MINUTE, 40);
				Date out3 = cal.getTime();
				createBill(tables.get(4), staff3, in3, out3, "PAID",
						List.of(new DetailItem(products.get(10), 2, 35000)), em);

				cal.add(Calendar.HOUR_OF_DAY, -1);
				Date in4 = cal.getTime();
				cal.add(Calendar.MINUTE, 55);
				Date out4 = cal.getTime();
				createBill(tables.get(6), staff4, in4, out4, "PAID",
						List.of(new DetailItem(products.get(17), 1, 45000),
								new DetailItem(products.get(18), 1, 48000),
								new DetailItem(products.get(3), 1, 32000)),
						em);

				// 6. Tạo 2 hóa đơn đang phục vụ (UNPAID) để Bàn 02 và Bàn 04 hiển thị MÀU ĐỎ
				cal = Calendar.getInstance();
				cal.add(Calendar.MINUTE, -25);
				createBill(tables.get(1), staff1, cal.getTime(), null, "UNPAID",
						List.of(new DetailItem(products.get(3), 1, 32000), new DetailItem(products.get(10), 1, 35000)), em);

				cal = Calendar.getInstance();
				cal.add(Calendar.MINUTE, -10);
				createBill(tables.get(3), staff4, cal.getTime(), null, "UNPAID",
						List.of(new DetailItem(products.get(18), 1, 48000),
								new DetailItem(products.get(16), 1, 42000),
								new DetailItem(products.get(21), 1, 40000)),
						em);

				System.out.println("[Seeder] Da nap thanh cong 6 nhan vien, 16 ban, 6 danh muc, 24 mon va 6 hoa don mau!");
			}

			em.getTransaction().commit();
		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			System.err.println("[Seeder] Loi khoi tao du lieu: " + e.getMessage());
			e.printStackTrace();
		} finally {
			em.close();
		}
	}

	private static Employee createEmp(String username, String passwordHash, String fullName, String role,
			EntityManager em) {
		Employee emp = new Employee();
		emp.setUsername(username);
		emp.setPassword(passwordHash);
		emp.setFullName(fullName);
		emp.setRole(role);
		emp.setActive(true);
		em.persist(emp);
		return emp;
	}

	private static Category createCat(String name, EntityManager em) {
		Category c = new Category();
		c.setName(name);
		em.persist(c);
		return c;
	}

	private static Product createProd(String name, float price, Category cat, EntityManager em) {
		Product p = new Product();
		p.setName(name);
		p.setPrice(price);
		p.setCategory(cat);
		p.setActive(true);
		em.persist(p);
		return p;
	}

	private static class DetailItem {
		final Product p;
		final int qty;
		final float price;

		DetailItem(Product p, int qty, float price) {
			this.p = p;
			this.qty = qty;
			this.price = price;
		}
	}

	private static void createBill(CoffeeTable table, Employee emp, Date checkIn, Date checkOut, String status,
			List<DetailItem> items, EntityManager em) {
		Bill bill = new Bill();
		bill.setTable(table);
		bill.setEmployee(emp);
		bill.setCheckInTime(checkIn);
		bill.setCheckOutTime(checkOut);
		bill.setStatus(status);

		BigDecimal total = BigDecimal.ZERO;
		List<BillDetail> details = new ArrayList<>();
		for (DetailItem it : items) {
			BillDetail d = new BillDetail();
			d.setBill(bill);
			d.setProduct(it.p);
			d.setQuantity(it.qty);
			d.setPrice(it.price);
			total = total.add(BigDecimal.valueOf(it.price).multiply(BigDecimal.valueOf(it.qty)));
			details.add(d);
		}
		bill.setTotalAmount(total.floatValue());
		bill.setDetails(details);
		em.persist(bill);
	}
}
