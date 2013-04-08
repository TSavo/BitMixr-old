package com.bitmixr;

import java.math.BigInteger;

public class NetworkRecieve {

	public String payerId;
	public String payeeId;
	public BigInteger amountSpent;
	public String getPayerId() {
		return payerId;
	}
	public void setPayerId(String payerId) {
		this.payerId = payerId;
	}
	public String getPayeeId() {
		return payeeId;
	}
	public void setPayeeId(String payeeId) {
		this.payeeId = payeeId;
	}
	public BigInteger getAmountSpent() {
		return amountSpent;
	}
	public void setAmountSpent(BigInteger amountSpent) {
		this.amountSpent = amountSpent;
	}
	public NetworkRecieve(String payerId, String payeeId, BigInteger amountSpent) {
		super();
		this.payerId = payerId;
		this.payeeId = payeeId;
		this.amountSpent = amountSpent;
	}


	

}
