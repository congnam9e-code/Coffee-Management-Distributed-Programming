package com.cafe.server.dao;

import com.cafe.common.entity.Bill;
import com.cafe.common.entity.BillDetail;
import com.cafe.common.entity.Category;
import com.cafe.common.entity.CoffeeTable;
import com.cafe.common.entity.Employee;
import com.cafe.common.entity.Product;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class OrderDao {

	public List<CoffeeTable> getAllTables() {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			return em.createQuery("SELECT t FROM CoffeeTable t ORDER BY t.id ASC", CoffeeTable.class).getResultList();
		} finally {
			em.close();
		}
	}

	public List<Bill> getAllBills() {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			List<Bill> bills = em.createQuery(
					"SELECT b FROM Bill b LEFT JOIN FETCH b.table LEFT JOIN FETCH b.employee ORDER BY b.id DESC",
					Bill.class).getResultList();
			List<Bill> result = new ArrayList<>(bills.size());
			for (Bill b : bills) {
				result.add(copyBillForTransfer(b, false));
			}
			return result;
		} finally {
			em.close();
		}
	}

	public Bill getActiveBill(int tableId) {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			List<Bill> bills = em.createQuery(
					"SELECT DISTINCT b FROM Bill b "
							+ "LEFT JOIN FETCH b.table "
							+ "LEFT JOIN FETCH b.employee "
							+ "LEFT JOIN FETCH b.details d "
							+ "LEFT JOIN FETCH d.product p "
							+ "LEFT JOIN FETCH p.category "
							+ "WHERE b.table.id = :tid AND b.status = 'UNPAID' "
							+ "ORDER BY b.id DESC",
					Bill.class).setParameter("tid", tableId).getResultList();

			if (bills.isEmpty()) {
				return null;
			}
			return copyBillForTransfer(bills.get(0), true);
		} finally {
			em.close();
		}
	}

	public DaoResult<Void> addToBill(int tableId, int employeeId, int productId, int quantity) {
		if (quantity <= 0) {
			return DaoResult.fail("Số lượng món phải lớn hơn 0!");
		}

		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();

			// Khóa bi quan trên chính bàn được chọn để chống race condition tạo trùng Bill UNPAID hoặc lost update
			CoffeeTable table = em.find(CoffeeTable.class, tableId, LockModeType.PESSIMISTIC_WRITE);
			if (table == null) {
				em.getTransaction().rollback();
				return DaoResult.fail("Bàn không tồn tại!");
			}

			Employee emp = em.find(Employee.class, employeeId);
			if (emp == null || !emp.isActive()) {
				em.getTransaction().rollback();
				return DaoResult.fail("Tài khoản nhân viên không hợp lệ hoặc đã bị khóa!");
			}

			Product product = em.find(Product.class, productId);
			if (product == null || !product.isActive()) {
				em.getTransaction().rollback();
				return DaoResult.fail("Món được chọn không tồn tại hoặc đã ngừng phục vụ!");
			}

			List<Bill> bills = em.createQuery(
					"SELECT DISTINCT b FROM Bill b LEFT JOIN FETCH b.details d LEFT JOIN FETCH d.product "
							+ "WHERE b.table.id = :tid AND b.status = 'UNPAID' ORDER BY b.id DESC",
					Bill.class).setParameter("tid", tableId).getResultList();

			Bill activeBill;
			if (bills.isEmpty()) {
				activeBill = new Bill();
				activeBill.setTable(table);
				activeBill.setEmployee(emp);
				activeBill.setCheckInTime(new Date());
				activeBill.setStatus("UNPAID");
				activeBill.setTotalAmount(0);
				activeBill.setDetails(new ArrayList<>());
				em.persist(activeBill);

				table.setStatus("OCCUPIED");
			} else {
				activeBill = bills.get(0);
				if (activeBill.getDetails() == null) {
					activeBill.setDetails(new ArrayList<>());
				}
			}

			// Chỉ cộng dồn khi trùng món VÀ cùng đơn giá để tránh lệch Bill.totalAmount khi giá món đổi giữa chừng
			BillDetail matchingDetail = null;
			for (BillDetail d : activeBill.getDetails()) {
				if (d.getProduct() != null
						&& d.getProduct().getId() == productId
						&& Float.compare(d.getPrice(), product.getPrice()) == 0) {
					matchingDetail = d;
					break;
				}
			}

			if (matchingDetail != null) {
				matchingDetail.setQuantity(matchingDetail.getQuantity() + quantity);
			} else {
				BillDetail detail = new BillDetail();
				detail.setBill(activeBill);
				detail.setProduct(product);
				detail.setQuantity(quantity);
				detail.setPrice(product.getPrice());
				em.persist(detail);
				activeBill.getDetails().add(detail);
			}

			recalculateTotal(activeBill);
			if (RequestContext.rollbackIfExpired(em)) {
				return DaoResult.fail("Yêu cầu quá thời gian xử lý trên Server, thao tác đặt món đã được hủy.");
			}
			em.getTransaction().commit();
			return DaoResult.ok("Thêm món thành công!");
		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
			return DaoResult.fail("Lỗi hệ thống khi thêm món: " + e.getMessage());
		} finally {
			em.close();
		}
	}

	public DaoResult<Void> checkoutBill(int billId) {
		EntityManager em = JPAUtil.getEntityManagerFactory().createEntityManager();
		try {
			em.getTransaction().begin();

			List<Integer> tableIds = em.createQuery(
					"SELECT t.id FROM Bill b LEFT JOIN b.table t WHERE b.id = :bid", Integer.class)
					.setParameter("bid", billId)
					.getResultList();
			if (tableIds.isEmpty()) {
				em.getTransaction().rollback();
				return DaoResult.fail("Không tìm thấy hóa đơn cần thanh toán!");
			}

			// Giữ đúng thứ tự khóa (CoffeeTable -> Bill) như addToBill để tránh deadlock
			Integer tableId = tableIds.get(0);
			CoffeeTable table = tableId != null
					? em.find(CoffeeTable.class, tableId, LockModeType.PESSIMISTIC_WRITE)
					: null;
			Bill bill = em.find(Bill.class, billId);
			if (bill == null) {
				em.getTransaction().rollback();
				return DaoResult.fail("Không tìm thấy hóa đơn cần thanh toán!");
			}
			if (!"UNPAID".equalsIgnoreCase(bill.getStatus())) {
				em.getTransaction().rollback();
				return DaoResult.fail("Hóa đơn này đã được thanh toán trước đó!");
			}

			recalculateTotal(bill);
			bill.setStatus("PAID");
			bill.setCheckOutTime(new Date());

			if (table != null) {
				table.setStatus("AVAILABLE");
			}

			if (RequestContext.rollbackIfExpired(em)) {
				return DaoResult.fail("Yêu cầu quá thời gian xử lý trên Server, thao tác thanh toán đã được hủy.");
			}
			em.getTransaction().commit();
			return DaoResult.ok("Thanh toán hóa đơn thành công!");
		} catch (Exception e) {
			if (em.getTransaction().isActive()) {
				em.getTransaction().rollback();
			}
			e.printStackTrace();
			return DaoResult.fail("Lỗi hệ thống khi thanh toán: " + e.getMessage());
		} finally {
			em.close();
		}
	}

	private void recalculateTotal(Bill bill) {
		BigDecimal total = BigDecimal.ZERO;
		if (bill.getDetails() != null) {
			for (BillDetail d : bill.getDetails()) {
				BigDecimal lineTotal = BigDecimal.valueOf(d.getPrice())
						.multiply(BigDecimal.valueOf(d.getQuantity()));
				total = total.add(lineTotal);
			}
		}
		bill.setTotalAmount(total.floatValue());
	}

	private Bill copyBillForTransfer(Bill src, boolean includeDetails) {
		Bill copy = new Bill();
		copy.setId(src.getId());
		copy.setCheckInTime(src.getCheckInTime());
		copy.setCheckOutTime(src.getCheckOutTime());
		copy.setTotalAmount(src.getTotalAmount());
		copy.setStatus(src.getStatus());

		if (src.getTable() != null) {
			CoffeeTable tCopy = new CoffeeTable();
			tCopy.setId(src.getTable().getId());
			tCopy.setName(src.getTable().getName());
			tCopy.setStatus(src.getTable().getStatus());
			copy.setTable(tCopy);
		}
		copy.setEmployee(EmployeeDao.sanitize(src.getEmployee()));

		List<BillDetail> cleanDetails = new ArrayList<>();
		if (includeDetails && src.getDetails() != null) {
			for (BillDetail bd : src.getDetails()) {
				BillDetail dCopy = new BillDetail();
				dCopy.setId(bd.getId());
				dCopy.setQuantity(bd.getQuantity());
				dCopy.setPrice(bd.getPrice());
				dCopy.setBill(null);
				if (bd.getProduct() != null) {
					Product pCopy = new Product();
					pCopy.setId(bd.getProduct().getId());
					pCopy.setName(bd.getProduct().getName());
					pCopy.setPrice(bd.getProduct().getPrice());
					pCopy.setActive(bd.getProduct().isActive());
					if (bd.getProduct().getCategory() != null) {
						Category cCopy = new Category();
						cCopy.setId(bd.getProduct().getCategory().getId());
						cCopy.setName(bd.getProduct().getCategory().getName());
						pCopy.setCategory(cCopy);
					}
					dCopy.setProduct(pCopy);
				}
				cleanDetails.add(dCopy);
			}
		}
		copy.setDetails(cleanDetails);
		return copy;
	}
}
