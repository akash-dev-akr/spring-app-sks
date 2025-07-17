package com.akrcode.akr.serviceImpl;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.akrcode.akr.dao.ContactDao;
import com.akrcode.akr.dto.SearchKeys;
import com.akrcode.akr.model.ContactModel;
import com.akrcode.akr.service.ContactService;

@Service
public class ContactServiceImpl implements ContactService {
	@Autowired
	private ContactDao contactDao;

	public String createContact(ContactModel contact) {
		return contactDao.saveContact(contact);
	}

	public ContactModel getContactById(Long id) {
		return contactDao.getContactById(id);
	}

	public String deleteContact(Long id) {
		return contactDao.deleteContact(id);
	}

	@Override
	public Map<String, Object> searchFilter(SearchKeys keyvalues) {
		return contactDao.searchFilter(keyvalues);
	}

	@Override
	public String readExcel() {
		return contactDao.readExcel();
	}
}