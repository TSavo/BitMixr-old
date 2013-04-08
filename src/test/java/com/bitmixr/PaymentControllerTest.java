package com.bitmixr;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.WrongNetworkException;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "Bitmixr-servlet.xml" })
public class PaymentControllerTest {

	@PersistenceContext
	EntityManager entityManager;
	final NetworkParameters params = NetworkParameters.prodNet();
	BlockStore blockStore;
	BlockChain chain;
	PeerGroup peerGroup;

	@Autowired PaymentController paymentController;
	
	@Test
	@Transactional
	public void testMe() throws BlockStoreException, WrongNetworkException, AddressFormatException, InterruptedException, ScriptException {
		Payment payment = new Payment();
		payment.setDestinationAddress("1MadiZrVZ4urpTHUTn6UTeWfSkEpb8TXk6");
		paymentController.add(payment);
	}
}
