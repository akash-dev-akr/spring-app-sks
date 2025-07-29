package com.akrcode.akr.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndentController {

	@GetMapping("/indent")
	public String dashboardPage() {
		return "forward:/indent.html";
	}
}
