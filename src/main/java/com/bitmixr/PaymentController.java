package com.bitmixr;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(value = "/")
public class PaymentController {

	@PersistenceContext
	protected EntityManager entityManager;

	@RequestMapping(method = RequestMethod.POST)
	@Transactional
	public @ResponseBody
	Payment add(@RequestBody final Payment aPayment) {
		aPayment.setSentAmount(0.0);
		aPayment.setRecievedAmount(0.0);
		entityManager.persist(aPayment);
		return aPayment;
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	@Transactional
	public @ResponseBody
	Payment show(@PathVariable final String id) {
		return entityManager.find(Payment.class, id);
	}
}
