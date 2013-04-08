package com.bitmixr;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import java.math.BigInteger;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

import com.google.bitcoin.core.AbstractBlockChain;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.HeadersMessage;
import com.google.bitcoin.core.MemoryPool;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.Peer;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.ScriptException;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.Wallet;
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
		Logger.getRootLogger().setLevel(Level.DEBUG);
		Logger.getLogger(com.google.bitcoin.core.BitcoinSerializer.class).setLevel(Level.WARN);
		Logger.getLogger(Wallet.class).setLevel(Level.INFO);
		Logger.getLogger(MemoryPool.class).setLevel(Level.INFO);
		Logger.getLogger(AbstractBlockChain.class).setLevel(Level.INFO);
		Logger.getLogger(HeadersMessage.class).setLevel(Level.INFO);
		Logger.getLogger(Peer.class).setLevel(Level.INFO);
		while(!paymentController.started.get()){
			Thread.sleep(500);
		}
		Payment payment = new Payment();
		payment.setDestinationAddress("1MadiZrVZ4urpTHUTn6UTeWfSkEpb8TXk6");
		Payment outpay = paymentController.add(payment);
		
		WalletActor walletActor = paymentController.actors.get(outpay.getId());
		Transaction transaction = Mockito.mock(Transaction.class);
		when(transaction.getValueSentToMe(any(Wallet.class))).thenReturn(BigInteger.ONE);
		when(transaction.getHashAsString()).thenReturn("Madigan");
		walletActor.onCoinsReceived(walletActor.wallet, transaction, BigInteger.ZERO, BigInteger.ONE);
		
	}
}
