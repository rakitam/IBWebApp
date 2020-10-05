package ib.project.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import ib.project.model.Authority;
import ib.project.repository.AuthorityRepository;

@Service
public class AuthorityService implements AuthorityServiceInterface {

	@Autowired
	AuthorityRepository authorityRepository;
	
	@Override
	public Authority findbyName(String name) {
		return authorityRepository.findByName(name);
	}

	
}
