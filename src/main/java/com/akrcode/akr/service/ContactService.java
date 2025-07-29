package com.akrcode.akr.service;

import java.util.Map;

import com.akrcode.akr.dto.SearchKeys;
import com.akrcode.akr.model.ContactModel;

public interface ContactService {

	String createContact(ContactModel contact);

	ContactModel getContactById(Long id);

	String deleteContact(Long id);

	Map<String, Object> searchFilter(SearchKeys keyvalues);

	String readExcel();

}
