package com.bitmixr;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.junit.Test;

public class TestBigList {

	@Test
	public void main() throws IOException{
		BufferedReader reader = new BufferedReader(new FileReader("d:\\alluniq.txt"));
		while(true){
			System.out.println(reader.readLine());
		}
	}
}
