package com.bitmixr;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javassist.NotFoundException;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import com.cpn.apiomatic.rest.RestCommand;
import com.google.bitcoin.core.Address;
import com.google.bitcoin.core.AddressFormatException;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.ECKey;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.PeerGroup;
import com.google.bitcoin.core.Transaction;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.core.Wallet.SendRequest;
import com.google.bitcoin.core.WrongNetworkException;
import com.google.bitcoin.discovery.DnsDiscovery;
import com.google.bitcoin.discovery.SeedPeers;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;
import com.google.bitcoin.store.MemoryFullPrunedBlockStore;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

@Controller
@RequestMapping(value = "/")
public class PaymentController extends DefaultExceptionHandler {

	@PersistenceContext
	protected EntityManager entityManager;

	BlockStore blockStore;
	BlockChain chain;
	PeerGroup peerGroup;
	NetworkParameters params = NetworkParameters.prodNet();

	Map<String, WalletActor> actors = new HashMap<>();
	ReentrantReadWriteLock actorsLock = new ReentrantReadWriteLock();
	ReentrantLock lock = new ReentrantLock();

	AtomicBoolean started = new AtomicBoolean(false);

	List<NetworkReceive> networkReceives = new ArrayList<>();
	ReentrantLock networkReceivesLock = new ReentrantLock();

	ExecutorService executor = Executors.newCachedThreadPool();

	public BlockStore getBlockStore() {
		return blockStore;
	}

	public void setBlockStore(BlockStore blockStore) {
		this.blockStore = blockStore;
	}

	public BlockChain getChain() {
		return chain;
	}

	public void setChain(BlockChain chain) {
		this.chain = chain;
	}

	public PeerGroup getPeerGroup() {
		return peerGroup;
	}

	public void setPeerGroup(PeerGroup peerGroup) {
		this.peerGroup = peerGroup;
	}

	public NetworkParameters getParams() {
		return params;
	}

	public void setParams(NetworkParameters params) {
		this.params = params;
	}

	public Map<String, WalletActor> getActors() {
		return actors;
	}

	public void setActors(Map<String, WalletActor> actors) {
		this.actors = actors;
	}

	public ReentrantReadWriteLock getActorsLock() {
		return actorsLock;
	}

	public void setActorsLock(ReentrantReadWriteLock actorsLock) {
		this.actorsLock = actorsLock;
	}

	public ReentrantLock getLock() {
		return lock;
	}

	public void setLock(ReentrantLock lock) {
		this.lock = lock;
	}

	public AtomicBoolean getStarted() {
		return started;
	}

	public void setStarted(AtomicBoolean started) {
		this.started = started;
	}

	public List<NetworkReceive> getNetworkRecieves() {
		return networkReceives;
	}

	public void setNetworkRecieves(List<NetworkReceive> networkRecieves) {
		this.networkReceives = networkRecieves;
	}

	public ReentrantLock getNetworkRecieveslock() {
		return networkReceivesLock;
	}

	public void setNetworkRecieveslock(ReentrantLock networkRecieveslock) {
		this.networkReceivesLock = networkRecieveslock;
	}

	public ExecutorService getExecutor() {
		return executor;
	}

	public void setExecutor(ExecutorService executor) {
		this.executor = executor;
	}

	public void init() throws BlockStoreException {
		Logger.getRootLogger().setLevel(Level.INFO);
		Logger.getLogger(com.google.bitcoin.core.BitcoinSerializer.class).setLevel(Level.WARN);

		blockStore = new MemoryFullPrunedBlockStore(params, 10000);
		System.out.println("Connecting ...");
		chain = new BlockChain(params, blockStore);
		peerGroup = new PeerGroup(params, chain);
		peerGroup.setUserAgent("BitMixr", "1.0");
		peerGroup.addPeerDiscovery(new SeedPeers(params));
		peerGroup.addPeerDiscovery(new DnsDiscovery(params));
		peerGroup.setFastCatchupTimeSecs(new GregorianCalendar(2013, 4, 1).getTimeInMillis() / 1000);
	}

	@Transactional
	public void load() {
		actorsLock.writeLock().lock();
		List<Payment> payments = entityManager.createQuery("from Payment", Payment.class).getResultList();
		for (Payment p : payments) {
			Wallet wallet = new Wallet(params);
			ECKey key = p.getECKey();
			System.out.println("Address is " + key.toAddress(params).toString() + ", Private Key is: " + key.getPrivateKeyEncoded(params).toString());
			wallet.addKey(key);
			peerGroup.addWallet(wallet);
			chain.addWallet(wallet);
			WalletActor actor = new WalletActor(wallet, p.getId());
			actorsLock.writeLock().lock();
			actors.put(p.getId(), actor);
			actorsLock.writeLock().unlock();
		}
		actorsLock.writeLock().unlock();

	}

	public void startUp() {
		peerGroup.startAndWait();
		peerGroup.waitForPeers(2);
		peerGroup.downloadBlockChain();
	}

	@Scheduled(fixedDelay = 20000)
	@Transactional
	public void checkSeenTransactions() throws BlockStoreException, IOException {
		lock.lock();
		if (!started.get()) {
			init();
			load();
			startUp();
			started.set(true);
		}
		actorsLock.readLock().lock();
		for (WalletActor actor : actors.values()) {
			actor.seenTransactionLock.writeLock().lock();
			for (Iterator<SeenTransaction> i = actor.seenTransactions.iterator(); i.hasNext();) {
				SeenTransaction seenTransaction = i.next();
				if (seenTransaction.getDirty().get()) {
					Payment payment = entityManager.find(Payment.class, actor.paymentId);
					try {
						entityManager.createQuery("from SeenTransaction where transactionHash = ? and payment = ?", SeenTransaction.class).setParameter(1, seenTransaction.getTransactionHash()).setParameter(2, payment).getSingleResult();
					} catch (Exception e) {

						seenTransaction.setPayment(payment);
						entityManager.persist(seenTransaction);
						payment.getSeenTransactions().add(seenTransaction);
						BigInteger originalAmount = seenTransaction.getAmount();
						BigInteger tip = new BigDecimal(originalAmount).multiply(new BigDecimal(2)).divide(new BigDecimal(100)).toBigInteger();
						payment.setRecievedAmount(payment.getRecievedAmount().add(originalAmount.subtract(tip)));
						payment.setReceivedTip(payment.getReceivedTip().add(tip));
						entityManager.merge(payment);
					}
					i.remove();
				}
			}
			actor.seenTransactionLock.writeLock().unlock();
		}
		actorsLock.readLock().unlock();
		lock.unlock();
	}

	@Transactional
	@Scheduled(fixedDelay = 20000)
	public void payOut() {
		lock.lock();
		List<Payment> paymentsNeedingSending = entityManager.createQuery("from Payment where sentAmount < recievedAmount and recievedAmount - sentAmount > 1000000", Payment.class).getResultList();
		for (final Payment payee : paymentsNeedingSending) {
			if (payee.getUpdatedOn() != null && payee.getUpdatedOn().getTime() > System.currentTimeMillis() - 30000) {
				continue;
			}
			// (sent - spent) = available
			// available > 0.01 && ! this payment

			List<Payment> paymentsAvailableForSending = entityManager.createQuery("from Payment where recievedAmount - spentAmount > 1000000 and id != ?", Payment.class).setParameter(1, payee.getId()).getResultList();
			if (paymentsAvailableForSending.size() < 1) {
				continue;
			}
			Collections.shuffle(paymentsAvailableForSending);
			Payment tempPayer = null;
			for (Payment p : paymentsAvailableForSending) {
				if (p.getUpdatedOn() != null && p.getUpdatedOn().getTime() > System.currentTimeMillis() - 30000) {
					continue;
				}
				tempPayer = p;
				break;
			}
			if (tempPayer == null) {
				continue;
			}
			final Payment payer = tempPayer;
			BigInteger amountAvailableForSending = payer.getRecievedAmount().subtract(payer.getSpentAmount());
			BigInteger amountNeedingToBeSent = payee.getRecievedAmount().subtract(payee.getSentAmount());
			final BigInteger amountToSend;

			if (amountAvailableForSending.compareTo(amountNeedingToBeSent) == 0) {
				amountToSend = amountAvailableForSending;
			} else if (amountAvailableForSending.compareTo(amountNeedingToBeSent) > 0) {
				amountToSend = amountNeedingToBeSent;
			} else {
				amountToSend = amountAvailableForSending;
			}

			// Now send the coins back!
			final Address from;
			try {
				from = new Address(params, payee.getDestinationAddress());
			} catch (AddressFormatException e1) {
				throw new RuntimeException(e1.getMessage(), e1);
			}

			
			Transaction tx = new Transaction(params);
			tx.addOutput(amountToSend, from);
			BigInteger tip = BigInteger.ZERO;
			if(payer.getReceivedTip().subtract(payer.getSpentTip()).compareTo(BigInteger.ZERO) > 0){
				AddressResponse tipAddress = new RestCommand<String, AddressResponse>("https://blockchain.info/merchant/e3625e3b-81ff-830e-9fcd-59c64bd4ade9/new_address?password=wookiechew&second_password=rockandroll!!!&label=" + payer.getId(), AddressResponse.class).get();
				tip = payer.getReceivedTip().subtract(payer.getSpentTip());
				try {
					tx.addOutput(tip, new Address(params, tipAddress.getAddress()));
				} catch (WrongNetworkException e) {
					throw new RuntimeException(e.getMessage(), e);
				} catch (AddressFormatException e) {
					throw new RuntimeException(e.getMessage(), e);
				}
			}
			final BigInteger realTip = tip;
			SendRequest request = Wallet.SendRequest.forTx(tx);
			actorsLock.readLock().lock();
			Wallet wallet = actors.get(payer.getId()).wallet;
			actorsLock.readLock().unlock();
			if (!wallet.completeTx(request))
				return; // Insufficient funds.
			// Ensure these funds won't be spent
			// again.
			try {
				wallet.commitTx(request.tx);
			} catch (VerificationException e) {
				throw new RuntimeException(e.getMessage(), e);
			}
			// wallet.saveToFile(...);
			// A proposed transaction is now sitting
			// in request.tx - send it in the
			// background.

			Futures.addCallback(peerGroup.broadcastTransaction(request.tx), new FutureCallback<Transaction>() {
				@Override
				public void onFailure(Throwable t) {
					t.printStackTrace();
				}

				@Override
				public void onSuccess(Transaction result) {
					networkReceivesLock.lock();
					networkReceives.add(new NetworkReceive(payer.getId(), payee.getId(), amountToSend, realTip));
					networkReceivesLock.unlock();
				}
			}, executor);
			payer.setUpdatedOn(new Date());
			payee.setUpdatedOn(new Date());
			entityManager.merge(payer);
			entityManager.merge(payee);
		}
		lock.unlock();
	}

	@Scheduled(fixedDelay = 2000)
	@Transactional
	public void commitSends() {
		lock.lock();
		networkReceivesLock.lock();
		for (NetworkReceive send : networkReceives) {
			Payment payer = entityManager.find(Payment.class, send.getPayerId());
			Payment payee = entityManager.find(Payment.class, send.getPayeeId());
			payer.setSpentAmount(payer.getSpentAmount().add(send.getAmountSpent()));
			payer.setSpentTip(payer.getSpentTip().add(send.getTip()));
			payee.setSentAmount(payee.getSentAmount().add(send.getAmountSpent()));
			payer.setUpdatedOn(null);
			payee.setUpdatedOn(null);
			payer = entityManager.merge(payer);
			payee = entityManager.merge(payee);
		}
		networkReceives.clear();
		networkReceivesLock.unlock();
		lock.unlock();
	}
	
	@Transactional
	@Scheduled(fixedDelay=60000)
	public void expirePayments(){
		lock.lock();
		List<Payment> payments = entityManager.createQuery("from Payment where (expiresOn not null and expiresOn < now()) or (totalToSend > 0 and sentAmount => totalToSend and (spentAmount = receivedAmount or recievedAmount - spentAmount < 1000000)", Payment.class).getResultList();
		for(Payment p : payments){
			ExpiredECKey key = new ExpiredECKey(p);
			entityManager.persist(key);
			entityManager.remove(p);
		}
		lock.unlock();
	}

	@RequestMapping(method = RequestMethod.POST)
	@Transactional
	public @ResponseBody
	Payment add(@RequestBody final Payment aPayment) {
		if (!started.get()) {
			// take the easy road home jack
			return null;
		}
		aPayment.setSentAmount(BigInteger.ZERO);
		aPayment.setRecievedAmount(BigInteger.ZERO);
		ECKey key = new ECKey();
		aPayment.setECKey(key);
		aPayment.setSourceAddress(key.toAddress(params).toString());
		Wallet wallet = new Wallet(params);
		wallet.addKey(key);
		WalletActor actor = new WalletActor(wallet, aPayment.getId());
		actorsLock.writeLock().lock();
		actors.put(aPayment.getId(), actor);
		actorsLock.writeLock().unlock();
		chain.addWallet(wallet);
		peerGroup.addWallet(wallet);
		System.out.println("Address is " + key.toAddress(params).toString() + ", Private Key is: " + key.getPrivateKeyEncoded(params).toString());
		entityManager.persist(aPayment);
		return aPayment;
	}

	@RequestMapping(value = "/{id}", method = RequestMethod.GET)
	@Transactional
	public @ResponseBody
	Payment show(@PathVariable final String id) throws NotFoundException {
		Payment payment;
		try {
			payment = entityManager.find(Payment.class, id);
		} catch (Exception e) {
			throw new NotFoundException("ID not found.");
		}
		if (!payment.isVisible()) {
			throw new NotFoundException("ID not found.");
		}
		return payment;
	}
}
