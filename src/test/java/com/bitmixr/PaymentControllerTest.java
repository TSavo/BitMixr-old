package com.bitmixr;


//@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(locations = { "Bitmixr-servlet.xml" })
public class PaymentControllerTest {

//	@PersistenceContext
//	EntityManager entityManager;
//	final NetworkParameters params = NetworkParameters.prodNet();
//	BlockStore blockStore;
//	BlockChain chain;
//	PeerGroup peerGroup;
//
//	@Autowired
//	PaymentController paymentController;
//
//	@Test
//	@Transactional
//	@Rollback(false) 
//	public void testMe() throws BlockStoreException, WrongNetworkException, AddressFormatException, InterruptedException, ScriptException {
////		Logger.getRootLogger().setLevel(Level.DEBUG);
////		Logger.getLogger(com.google.bitcoin.core.BitcoinSerializer.class).setLevel(Level.WARN);
////		Logger.getLogger(Wallet.class).setLevel(Level.INFO);
////		Logger.getLogger(MemoryPool.class).setLevel(Level.INFO);
////		Logger.getLogger(AbstractBlockChain.class).setLevel(Level.INFO);
////		Logger.getLogger(HeadersMessage.class).setLevel(Level.INFO);
////		Logger.getLogger(Peer.class).setLevel(Level.INFO);
////		while (!paymentController.getStarted().get()) {
////			Thread.sleep(500);
////		}
////		Payment payment = new Payment();
////		payment.setDestinationAddress("1MadiZrVZ4urpTHUTn6UTeWfSkEpb8TXk6");
////		Payment outpay = paymentController.add(payment);
////
////		WalletActor walletActor = paymentController.getActors().get(outpay.getId());
////		Transaction transaction = Mockito.mock(Transaction.class);
////		TransactionConfidence confidence = Mockito.mock(TransactionConfidence.class);
////		when(transaction.getConfidence()).thenReturn(confidence);
////		Mockito.doNothing().when(confidence).addEventListener(any(TransactionConfidence.Listener.class));
////
////		when(transaction.getValueSentToMe(any(Wallet.class))).thenReturn(BigInteger.ONE);
////		when(transaction.getHashAsString()).thenReturn("Madigan");
////		when(confidence.getConfidenceType()).thenReturn(TransactionConfidence.ConfidenceType.BUILDING);
////		walletActor.onCoinsReceived(walletActor.wallet, transaction, BigInteger.ZERO, BigInteger.ONE);
////		walletActor.seenTransactions.iterator().next().onConfidenceChanged(transaction);
////		Thread.sleep(Integer.MAX_VALUE);
//	}
}
