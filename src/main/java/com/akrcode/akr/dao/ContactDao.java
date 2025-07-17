package com.akrcode.akr.dao;

import com.akrcode.akr.dto.SearchKeys;
import com.akrcode.akr.model.ContactModel;
import java.util.List;
import java.util.Map;

public interface ContactDao {
	String saveContact(ContactModel contact);

	ContactModel getContactById(Long id);

	String deleteContact(Long id);

	String autoGeneraterId();

	Map<String, Object> searchFilter(SearchKeys keyvalues);

	String readExcel();

}
