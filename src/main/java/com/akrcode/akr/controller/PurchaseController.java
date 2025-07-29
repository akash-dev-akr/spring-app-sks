package com.akrcode.akr.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PurchaseController {

	@GetMapping("/purchase")
	public String showPurchasePage() {
	    return "forward:/index.html";  // ✅ serves HTML, but keeps /purchase in browser
	}

}