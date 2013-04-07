package com.bitmixr;

import java.math.BigInteger;

import com.google.bitcoin.core.Wallet;

public class WalletActor {

	public Wallet wallet;
	public BigInteger total = BigInteger.ZERO;
	
	public void addTotal(BigInteger in){
		total = total.add(in);
	}
}
