package com.cafe.common.entity;

import jakarta.persistence.*;
import java.io.Serializable;

@Entity
@Table(name = "Category")
public class Category implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@Column(nullable = false, columnDefinition = "nvarchar(255)")
	private String name;

	public Category() {
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

	@Override
	public String toString() {
		return name; // Rất quan trọng để hiển thị tên trên JComboBox (Swing)
	}
}
