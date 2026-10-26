package com.cafe.common.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "Bill")
public class Bill implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "table_id")
	private CoffeeTable table;

	@ManyToOne(fetch = FetchType.EAGER)
	@JoinColumn(name = "employee_id")
	private Employee employee;

	@Temporal(TemporalType.TIMESTAMP)
	private Date checkInTime;

	@Temporal(TemporalType.TIMESTAMP)
	private Date checkOutTime;

	private float totalAmount;

	private String status; // UNPAID, PAID

	@OneToMany(mappedBy = "bill", fetch = FetchType.EAGER, cascade = CascadeType.ALL)
	private List<BillDetail> details;

	public Bill() {
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public CoffeeTable getTable() {
		return table;
	}

	public void setTable(CoffeeTable table) {
		this.table = table;
	}

	public Employee getEmployee() {
		return employee;
	}

	public void setEmployee(Employee employee) {
		this.employee = employee;
	}

	public Date getCheckInTime() {
		return checkInTime;
	}

	public void setCheckInTime(Date checkInTime) {
		this.checkInTime = checkInTime;
	}

	public Date getCheckOutTime() {
		return checkOutTime;
	}

	public void setCheckOutTime(Date checkOutTime) {
		this.checkOutTime = checkOutTime;
	}

	public float getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(float totalAmount) {
		this.totalAmount = totalAmount;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public List<BillDetail> getDetails() {
		return details;
	}

	public void setDetails(List<BillDetail> details) {
		this.details = details;
	}
}
