package com.bitmixr.user;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.bitmixr.DefaultExceptionHandler;

@Controller
@RequestMapping(value = "/")
@Transactional
public class BitMixrController extends DefaultExceptionHandler {

	@PersistenceContext
	EntityManager entityManager;

	@RequestMapping(method = RequestMethod.POST)
	public @ResponseBody
	Payment requestPayment(@RequestBody Payment aRequest) {
		return aRequest;
	}
}
