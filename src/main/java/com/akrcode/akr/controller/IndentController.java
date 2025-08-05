package com.akrcode.akr.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndentController {

	 @GetMapping("/")
	    public String redirectRoot() {
	        return "redirect:/index";
	    }

	    @GetMapping("/index")
	    public String showIndex() {
	        return "forward:/index.html";
	    }

	    @GetMapping("/indent")
	    public String indentPage() {
	        return "forward:/index.html";
	    }
	    @GetMapping("/dashboard")
	    public String dashboardPage() {
	        return "forward:/dashboard.html";
	    }
	    @GetMapping("/purchase")
	    public String purchasePage() {
	        return "forward:/index.html";
	    }
}
