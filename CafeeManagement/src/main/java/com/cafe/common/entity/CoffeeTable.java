package com.cafe.common.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "CoffeeTable")
public class CoffeeTable implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@Column(nullable = false, columnDefinition = "nvarchar(255)")
	private String name;

	@Column(nullable = false)
	private String status;

	public CoffeeTable() {
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
}
