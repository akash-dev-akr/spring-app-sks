package com.akrcode.akr.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "set_used")
public class SetUsedModel {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String bom_code;
	private String bom_name;
	private String set_uom;
	private Integer used_set_qty;
	private Integer orgid;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getBom_code() {
		return bom_code;
	}

	public void setBom_code(String bom_code) {
		this.bom_code = bom_code;
	}

	public String getBom_name() {
		return bom_name;
	}

	public void setBom_name(String bom_name) {
		this.bom_name = bom_name;
	}

	public String getSet_uom() {
		return set_uom;
	}

	public void setSet_uom(String set_uom) {
		this.set_uom = set_uom;
	}

	public Integer getUsed_set_qty() {
		return used_set_qty;
	}

	public void setUsed_set_qty(Integer used_set_qty) {
		this.used_set_qty = used_set_qty;
	}

	public Integer getOrgid() {
		return orgid;
	}

	public void setOrgid(Integer orgid) {
		this.orgid = orgid;
	}

}
