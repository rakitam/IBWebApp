package ib.project.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bouncycastle.cert.X509CRLHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ib.project.certificate.model.IssuerData;
import ib.project.certificate.model.SubjectData;
import ib.project.certificate.utils.CRLManager;
import ib.project.certificate.utils.CertificateGenerator;
import ib.project.certificate.utils.KeyStoreCertificateGenerator;
import ib.project.dto.UserDTO;
import ib.project.keystore.KeyStoreWriter;
import ib.project.model.Authority;
import ib.project.model.User;
import ib.project.service.AuthorityServiceInterface;
import ib.project.service.UserServiceInterface;

@RestController
@RequestMapping(value = "api/users")
@CrossOrigin("*")
public class UserController {

	@Autowired
	private UserServiceInterface userService;

	@Autowired
	private AuthorityServiceInterface authorityService;

	@Autowired
	PasswordEncoder passwordEncoder;

	@GetMapping("/all")
	// @PreAuthorize("hasRole('ADMIN')")
	public List<User> getAll() {
		return this.userService.findAll();
	}

	@GetMapping(value = "/active-users")
	public ResponseEntity<List<UserDTO>> getActive() {
		List<UserDTO> active = new ArrayList<>();
		List<User> users = userService.findByActiveTrue();
		for (User user : users) {
			active.add(new UserDTO(user));
		}
		return new ResponseEntity<>(active, HttpStatus.OK);
	}

	@GetMapping(value = "/inactive-users")
	public ResponseEntity<List<UserDTO>> getInactive() {
		List<UserDTO> inactive = new ArrayList<>();
		List<User> users = userService.findByActiveFalse();
		for (User user : users) {
			inactive.add(new UserDTO(user));
		}
		return new ResponseEntity<>(inactive, HttpStatus.OK);
	}

	// User registration
	@PostMapping(value = "/register", consumes = "application/json")
	public ResponseEntity<UserDTO> register(@RequestBody UserDTO userDTO) {
		Authority authority = authorityService.findByName("REGULAR");
		User u = userService.findByEmail(userDTO.getEmail());
		if (u != null) {
			return new ResponseEntity<>(HttpStatus.FORBIDDEN);
		}

		// Kreiranje novog korisnika
		u = new User();
		u.setEmail(userDTO.getEmail());
		u.setPassword(passwordEncoder.encode(userDTO.getPassword()));
		// pri registraciji je user inicijalno false dok ga admin ne odobri
		u.setActive(false);
		u.getUserAuthorities().add(authority);

		u = userService.save(u);

		// Kreiranje korisničkog direktorijuma
		String id = Long.toString(u.getId());
		createDirectory(id);

		// Kreiranje KeyStore-a za korisnika
		if (generateKeyStore(u)) {
			u.setCertificate("data/" + id + "/keystore.jks");
		}

		return new ResponseEntity<>(new UserDTO(u), HttpStatus.OK);
	}

	// User activation
	@PutMapping(value = "/activate/{id}")
	// @PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<UserDTO> activateUser(@PathVariable("id") Long id) {
		User user = userService.findById(id);
		if (user == null) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		user.setActive(true);
		user = userService.save(user);
		return new ResponseEntity<>(new UserDTO(user), HttpStatus.OK);
	}

	@RequestMapping("/whoami")
	public User user(Principal user) {
		return this.userService.findByEmail(user.getName());
	}

	/*
	 * TODO://Premestiti kod koji se nalazi ispod u zasebnu klasu nakon testiranja
	 */
	
	// Kreiranje self-signed sertifikata i kreiranje keystore-a u korisnički
	// direktorijum
	private boolean generateKeyStore(User user) {
		CertificateGenerator cg = new CertificateGenerator();

		try {
			SimpleDateFormat iso8601Formater = new SimpleDateFormat("yyyy-MM-dd");
			Date startDate = iso8601Formater.parse("2019-01-01");
			Date endDate = iso8601Formater.parse("2024-01-01");
			KeyPair keyPairCA = cg.generateKeyPair();

			IssuerData issuerData = new IssuerData("FTN", "Fakultet tehnickih nauka", "Katedra za informatiku", "RS",
					"ftnmail@uns.ac.rs", "123445", keyPairCA.getPrivate());
			SubjectData subjectData1 = new SubjectData(keyPairCA.getPublic(), issuerData.getX500name(), "1", startDate,
					endDate);

			X509Certificate certCA = cg.generateCertificate(issuerData, subjectData1);
			X509CRLHolder crlHolder = CRLManager.createCRL(certCA, keyPairCA.getPrivate());

			// Kreiranje sertifikata potpisanog od strane CA
			startDate = iso8601Formater.parse("2020-10-7");
			endDate = iso8601Formater.parse("2021-10-7");

			KeyPair keyPair2 = cg.generateKeyPair();

			String email = user.getEmail();
			char[] password = email.toCharArray();
			String id = Long.toString(user.getId());

			SubjectData subjectData2 = new SubjectData(keyPair2.getPublic(), "FTN", email, "Katedra za informatiku",
					"RS", email, "12345", "1", startDate, endDate);
			X509Certificate cert = cg.generateCertificate(issuerData, subjectData2);

			// kreiranje i čuvanje keystore-a
			KeyStoreWriter keyStoreWriter = new KeyStoreWriter();
			keyStoreWriter.loadKeyStore(null, password);
			keyStoreWriter.write(email, keyPair2.getPrivate(), password, cert);
			keyStoreWriter.saveKeyStore("data/" + id + "/keystore.jks", password);

			return true;

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	// Kreiranje korisnickog direktorijuma
	private void createDirectory(String id) {

		Path userDir = Paths.get("data/" + id);

		try {
			Files.createDirectory(userDir);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
