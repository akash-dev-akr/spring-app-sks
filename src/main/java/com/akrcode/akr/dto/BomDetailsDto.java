package com.akrcode.akr.dto;

public class BomDetailsDto {
	private String ingredient;
	private String ideal_qty;
	private String actual_qty;
	private String variance;

	public String getIngredient() {
		return ingredient;
	}

	public void setIngredient(String ingredient) {
		this.ingredient = ingredient;
	}

	public String getIdeal_qty() {
		return ideal_qty;
	}

	public void setIdeal_qty(String ideal_qty) {
		this.ideal_qty = ideal_qty;
	}

	public String getActual_qty() {
		return actual_qty;
	}

	public void setActual_qty(String actual_qty) {
		this.actual_qty = actual_qty;
	}

	public String getVariance() {
		return variance;
	}

	public void setVariance(String variance) {
		this.variance = variance;
	}

}
