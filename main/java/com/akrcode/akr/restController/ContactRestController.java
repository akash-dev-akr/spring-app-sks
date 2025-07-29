package com.akrcode.akr.restController;

import com.akrcode.akr.dto.SearchKeys;
import com.akrcode.akr.model.ContactModel;
import com.akrcode.akr.service.ContactService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/contacts")
@CrossOrigin("*")
public class ContactRestController {

	@Autowired
	private ContactService contactService;

	@PostMapping("/save")	
	public String createContact(@RequestBody ContactModel contact) {
		return contactService.createContact(contact);
	}

	@GetMapping
	public Map<String, Object> searchFilter() {
		SearchKeys test=  new SearchKeys();
		return contactService.searchFilter(test);
	}

	
	@GetMapping("/{id}")
	public ContactModel getContactById(@PathVariable Long id) {
		return contactService.getContactById(id);
	}

	@DeleteMapping("/{id}")
	public String deleteContact(@PathVariable Long id) {
		return contactService.deleteContact(id);
	}

}
