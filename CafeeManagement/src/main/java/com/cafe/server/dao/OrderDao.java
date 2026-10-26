package com.cafe.server.dao;

import com.cafe.common.entity.CoffeeTable;
import jakarta.persistence.EntityManager;
import java.util.List;

public class OrderDao {
	public List<CoffeeTable> getAllTables() {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			return em.createQuery("SELECT t FROM CoffeeTable t", CoffeeTable.class).getResultList();
		} finally {
			em.close();
		}
	}

	public List<com.cafe.common.entity.Bill> getAllBills() {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			List<com.cafe.common.entity.Bill> bills = em
					.createQuery("SELECT b FROM Bill b ORDER BY b.id DESC", com.cafe.common.entity.Bill.class)
					.getResultList();
			for (com.cafe.common.entity.Bill b : bills) {
				if (b.getDetails() != null) {
					for (com.cafe.common.entity.BillDetail bd : b.getDetails())
						bd.setBill(null);
				}
			}
			return bills;
		} finally {
			em.close();
		}
	}

	public com.cafe.common.entity.Bill getActiveBill(int tableId) {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			List<com.cafe.common.entity.Bill> bills = em
					.createQuery("SELECT b FROM Bill b WHERE b.table.id = :tid AND b.status = 'UNPAID'",
							com.cafe.common.entity.Bill.class)
					.setParameter("tid", tableId).getResultList();

			if (bills.isEmpty())
				return null;

			com.cafe.common.entity.Bill b = bills.get(0);
			// Cắt đứt tham chiếu ngược để tránh lỗi đệ quy (StackOverflow) khi truyền qua
			// mạng
			if (b.getDetails() != null) {
				for (com.cafe.common.entity.BillDetail bd : b.getDetails()) {
					bd.setBill(null);
				}
			}
			return b;
		} finally {
			em.close();
		}
	}

	public boolean addToBill(int tableId, int employeeId, int productId, int quantity) {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();

			com.cafe.common.entity.CoffeeTable table = em.find(com.cafe.common.entity.CoffeeTable.class, tableId);
			com.cafe.common.entity.Employee emp = em.find(com.cafe.common.entity.Employee.class, employeeId);
			com.cafe.common.entity.Product product = em.find(com.cafe.common.entity.Product.class, productId);

			if (table == null || product == null)
				return false;

			List<com.cafe.common.entity.Bill> bills = em
					.createQuery("SELECT b FROM Bill b WHERE b.table.id = :tid AND b.status = 'UNPAID'",
							com.cafe.common.entity.Bill.class)
					.setParameter("tid", tableId).getResultList();

			com.cafe.common.entity.Bill activeBill;
			if (bills.isEmpty()) {
				activeBill = new com.cafe.common.entity.Bill();
				activeBill.setTable(table);
				activeBill.setEmployee(emp);
				activeBill.setCheckInTime(new java.util.Date());
				activeBill.setStatus("UNPAID");
				activeBill.setTotalAmount(0);
				activeBill.setDetails(new java.util.ArrayList<>());
				em.persist(activeBill);

				table.setStatus("OCCUPIED");
				em.merge(table);
			} else {
				activeBill = bills.get(0);
			}

			// Kiểm tra món đã tồn tại trong hóa đơn chưa để cộng dồn số lượng
			boolean exists = false;
			if (activeBill.getDetails() != null) {
				for (com.cafe.common.entity.BillDetail d : activeBill.getDetails()) {
					if (d.getProduct() != null && d.getProduct().getId() == productId) {
						d.setQuantity(d.getQuantity() + quantity);
						em.merge(d);
						exists = true;
						break;
					}
				}
			}
			if (!exists) {
				com.cafe.common.entity.BillDetail detail = new com.cafe.common.entity.BillDetail();
				detail.setBill(activeBill);
				detail.setProduct(product);
				detail.setQuantity(quantity);
				detail.setPrice(product.getPrice());
				em.persist(detail);
				if (activeBill.getDetails() != null) {
					activeBill.getDetails().add(detail);
				}
			}

			activeBill.setTotalAmount(activeBill.getTotalAmount() + (product.getPrice() * quantity));
			em.merge(activeBill);

			em.getTransaction().commit();
			return true;
		} catch (Exception e) {
			if (em.getTransaction().isActive())
				em.getTransaction().rollback();
			e.printStackTrace();
			return false;
		} finally {
			em.close();
		}
	}

	public boolean checkoutBill(int billId) {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();
			com.cafe.common.entity.Bill bill = em.find(com.cafe.common.entity.Bill.class, billId);
			if (bill != null) {
				bill.setStatus("PAID");
				bill.setCheckOutTime(new java.util.Date());

				com.cafe.common.entity.CoffeeTable table = bill.getTable();
				if (table != null) {
					table.setStatus("AVAILABLE");
					em.merge(table);
				}

				em.merge(bill);
				em.getTransaction().commit();
				return true;
			}
			return false;
		} catch (Exception e) {
			if (em.getTransaction().isActive())
				em.getTransaction().rollback();
			e.printStackTrace();
			return false;
		} finally {
			em.close();
		}
	}
}
